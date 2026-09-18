# IaC_with_Terraform

A reference Terraform and Ansible setup for standing up a single AWS web server end to end: a VPC, a security group, an EC2 instance, and a Jenkins pipeline that chains `terraform apply` into an Ansible playbook run. It is meant as a compact example of wiring those three tools together, not as a production deployment template.

## What it provisions

`terraform/main.tf` wires three local modules together:

- `aws_vpc`: a VPC (`10.0.0.0/16` by default) with one public subnet, an internet gateway, and a route table.
- `ec2_security_group`: a security group allowing inbound TCP 22, 80, and 443 from `0.0.0.0/0`, and all outbound traffic.
- `aws_instance`: a single EC2 instance (`t3.micro` by default) placed in that subnet and security group.

`ansible/web_server.yml` then installs Apache on the instance and drops a static `index.html`. `jenkins_config/main.groovy` is a declarative Jenkins pipeline that clones this repo, runs `terraform init` and `terraform apply`, reads the resulting public IP, regenerates `ansible/inventory.ini`, and runs the playbook automatically.

## Requirements

- Terraform, with AWS credentials available to the provider (for example via `aws configure` or environment variables).
- An existing EC2 key pair in the target region matching the `key_name` set in `terraform/main.tf` (`project_demo` by default, region `eu-north-1`).
- Ansible, and SSH access to the resulting instance, to run the playbook stage.
- Jenkins, only if you use the pipeline in `jenkins_config/main.groovy` instead of running the two stages by hand.
- If using the pipeline: an `ANSIBLE_SSH_KEY` build parameter set to the absolute path of the `.pem` key on the Jenkins agent. The pipeline fails immediately if it is blank.

## Running it

Provision the infrastructure:

```bash
cd terraform
terraform init
terraform apply
```

Terraform prints the instance's `public_ip` output. Edit `ansible/inventory.ini` with that IP and the path to your private key, then configure the host:

```bash
cd ../ansible
ansible-playbook -i inventory.ini web_server.yml
```

## What the output means

A successful run leaves you with one running EC2 instance serving `Hello, this is my website!` over plain HTTP on port 80. The `public_ip` Terraform output is the address to browse to or SSH into.

## Limitations

- The security group opens SSH (port 22) to `0.0.0.0/0`. That is fine for a short-lived demo host but means anyone on the internet can attempt to authenticate against it; in any longer-lived setup, restrict the ingress CIDR to your own IP or a bastion.
- `ansible/inventory.ini` is a placeholder template: the host and key path read `REPLACE_WITH_PUBLIC_IP` and `REPLACE_WITH_PATH_TO_KEY.pem` and must be filled in to run the playbook by hand. The Jenkins pipeline overwrites the file instead, using the Terraform output and the `ANSIBLE_SSH_KEY` build parameter.
- The pipeline clones this repository from GitHub rather than using a Jenkins SCM checkout, so it always runs whatever is on the default branch regardless of which revision triggered the build.
- Terraform state is local (no remote backend configured), so this is set up for one operator at a time, not team use. `.gitignore` excludes the state files, because they record real resource ids and can contain sensitive attribute values.
- Because the state is local, the pipeline keeps its checkout in the job's workspace and refreshes it in place rather than re-cloning: the state file sits inside that directory, and losing it makes the next `apply` build a second complete stack and leave the first orphaned outside Terraform's reach. Three conditions break that and all have the same consequence, so the job needs to respect them: **it must not run concurrently** (the pipeline sets `disableConcurrentBuilds()`, since a second simultaneous build gets its own workspace and therefore its own state), **it must stay on one agent** (`agent any` is left in place because agent labels are site-specific; pin it by label if this Jenkins has more than one), and **the workspace must not be wiped** by a cleanup rule or by "Wipe out current workspace". Anyone adopting this for more than a demo should configure a remote backend with locking instead of relying on those three.
- If the checkout directory exists but is not a usable git checkout, the pipeline moves it aside to `iac-demo.broken.<epoch>` and clones fresh, rather than failing every build as it previously did. That tree may hold the only copy of the local state, so `terraform.tfstate` and its backup are copied across into the new checkout; without that step the build would apply from scratch and create a second stack. The moved-aside copies are kept deliberately, so nothing is discarded without a human looking; each is a full checkout, so clear old ones periodically.
- The VPC module creates a single subnet in a single availability zone; there is no redundancy.
- The pipeline runs `terraform apply -auto-approve` with no manual review step, and provisions real, billable AWS resources.

---
https://www.assumed-breach.com
