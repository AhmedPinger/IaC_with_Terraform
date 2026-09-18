pipeline {
    // Pin this to a single agent by label if this Jenkins has more than one.
    // The Terraform state for this project is local and lives in the workspace,
    // so a build landing on a different agent finds no state and applies from
    // scratch, building a second complete stack and orphaning the first.
    agent any

    options {
        // Two concurrent builds of the same job get two workspaces (Jenkins
        // suffixes the second @2) and therefore two separate Terraform states,
        // with the same duplicate-stack consequence.
        disableConcurrentBuilds()
    }

    environment {
        // The checkout lives in the job's workspace, not in /tmp. Terraform state
        // for this project is local and sits inside this directory, and /tmp
        // cleaners age out files individually: if the state file were removed
        // while .git survived, the next apply would build a second stack and
        // orphan the first outside Terraform's reach.
        REPO_DIR = "${env.WORKSPACE}/iac-demo"
    }

    parameters {
        string(
            name: 'ANSIBLE_SSH_KEY',
            defaultValue: '',
            description: 'Absolute path, on the Jenkins agent, to the EC2 private key (.pem) that Ansible uses to reach the instance. Required.'
        )
    }

    stages {
        stage('Check Parameters') {
            steps {
                script {
                    if (!params.ANSIBLE_SSH_KEY?.trim()) {
                        error('ANSIBLE_SSH_KEY is required. Set it to the path of the .pem file on the agent before running this pipeline.')
                    }
                    // Anyone who can start a build controls this value, and it reaches
                    // a shell. Restrict it to an absolute .pem path with no characters
                    // that mean anything to a shell.
                    if (!(params.ANSIBLE_SSH_KEY.trim() ==~ ~'^/[A-Za-z0-9._/-]+\\.pem$')) {
                        error('ANSIBLE_SSH_KEY must be an absolute path ending in .pem, using only letters, digits, dot, underscore, hyphen and slash.')
                    }
                }
            }
        }

        stage('Clone and Terraform Init') {
            steps {
                script {
                    // Refresh the checkout in place. Cloning unconditionally failed
                    // on every build after the first, because the destination already
                    // existed and was not empty. Deleting the directory instead is not
                    // an option: Terraform state is local and lives under
                    // terraform/, so removing the tree would lose it and the next
                    // apply would build a second stack and orphan the first. A reset
                    // updates tracked files and leaves untracked state alone.
                    sh '''
                        set -eu
                        if [ -d "$REPO_DIR/.git" ]; then
                            cd "$REPO_DIR"
                            git fetch --prune origin
                            git reset --hard origin/main
                        elif [ -d "$REPO_DIR" ]; then
                            # Present but not a usable checkout. Cloning into it would
                            # fail on every build until someone cleared it by hand, so
                            # move it aside and start fresh. These copies are kept
                            # rather than removed, so nothing is lost silently; clear
                            # old ones by hand, they are a full checkout each.
                            BROKEN="$REPO_DIR.broken.$(date +%s)"
                            mv "$REPO_DIR" "$BROKEN"
                            git clone https://github.com/AhmedPinger/IaC_with_Terraform.git "$REPO_DIR"
                            # The moved-aside tree may hold the only copy of the
                            # local Terraform state. Carry it across, or this build
                            # applies from scratch and creates a second complete
                            # stack while the first is left orphaned.
                            for f in terraform.tfstate terraform.tfstate.backup; do
                                if [ -f "$BROKEN/terraform/$f" ]; then
                                    cp -p "$BROKEN/terraform/$f" "$REPO_DIR/terraform/$f"
                                    echo "Recovered $f from $BROKEN"
                                fi
                            done
                        else
                            git clone https://github.com/AhmedPinger/IaC_with_Terraform.git "$REPO_DIR"
                        fi
                    '''

                    // Change to the Terraform directory and run terraform init
                    dir("${env.REPO_DIR}/terraform") {
                        sh 'terraform init'
                    }
                }
            }
        }

        stage('Terraform Apply') {
            steps {
                script {
                    // Change to the Terraform directory and run terraform apply
                    dir("${env.REPO_DIR}/terraform") {
                        sh 'terraform apply -auto-approve'
                    }

                    sleep(time: 1, unit: 'MINUTES')

                    // Get the IP address from Terraform output
                    def ipAddress = sh(script: 'cd "$REPO_DIR/terraform" && terraform output -raw public_ip', returnStdout: true).trim()

                    // Trim here as well: the guard's trim() only tested the value,
                    // and an untrimmed path breaks the ini host-line parser.
                    def sshKeyPath = params.ANSIBLE_SSH_KEY.trim()

                    // Pass both values through the environment rather than
                    // interpolating them into the shell command. Interpolating a
                    // build parameter into a quoted shell string lets a value
                    // containing a quote close it and run commands as the agent user,
                    // which holds the AWS credentials.
                    // The group name matches hosts: web_server in ansible/web_server.yml.
                    withEnv(["IP_ADDRESS=${ipAddress}", "KEY_PATH=${sshKeyPath}"]) {
                        sh '''
                            set -eu
                            INV="$REPO_DIR/ansible/inventory.ini"
                            printf '[web_server]\\n' > "$INV"
                            printf 'web_server_1 ansible_ssh_host=%s ansible_ssh_user=admin ansible_ssh_private_key_file=%s\\n' \\
                                "$IP_ADDRESS" "$KEY_PATH" >> "$INV"
                        '''
                    }

                    // Use ssh-keyscan to record the host key. The address comes from
                    // Terraform output rather than a parameter, but it is passed
                    // through the environment for the same reason as above.
                    withEnv(["IP_ADDRESS=${ipAddress}"]) {
                        sh '''
                            set -eu
                            mkdir -p ~/.ssh
                            ssh-keyscan -H "$IP_ADDRESS" >> ~/.ssh/known_hosts
                        '''
                    }
                }
            }
        }

        stage('Run Ansible Playbook') {
            steps {
                script {
                    // Change to the Ansible directory and run the playbook
                    dir("${env.REPO_DIR}/ansible") {
                        // Run the Ansible playbook without modifying ANSIBLE_SSH_ARGS
                        sh 'ansible-playbook -i inventory.ini web_server.yml'
                    }
                }
            }
        }
    }
}
