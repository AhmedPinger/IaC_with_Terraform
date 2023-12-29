pipeline {
    agent any

    stages {
        stage('Clone and Terraform Init') {
            steps {
                script {
                    // Clone the repository into /tmp
                    sh 'git clone https://github.com/AhmedPinger/IaC_with_Terraform.git /tmp/IaC_with_Terraform'

                    // Change to the Terraform directory and run terraform init
                    dir('/tmp/IaC_with_Terraform/terraform') {
                        sh 'terraform init'
                    }
                }
            }
        }

        stage('Terraform Apply') {
            steps {
                script {
                    // Change to the Terraform directory and run terraform apply
                    dir('/tmp/IaC_with_Terraform/terraform') {
                        sh 'terraform apply -auto-approve'
                    }

                    sleep(time: 1, unit: 'MINUTES')

                    // Get the IP address from Terraform output
                    def ipAddress = sh(script: '/bin/bash -c "cd /tmp/IaC_with_Terraform/terraform; terraform output -raw public_ip"', returnStdout: true).trim()

                    // Create the inventory file
                    sh "echo '[web_servers]' > /tmp/IaC_with_Terraform/ansible/inventory.ini"
                    sh "echo 'web_server ansible_ssh_host=${ipAddress} ansible_ssh_user=admin ansible_ssh_private_key_file=/Users/ahmedpinger/Downloads/project_demo.pem' >> /tmp/IaC_with_Terraform/ansible/inventory.ini"

                    // Use ssh-keyscan to fetch and append the host's public key to known_hosts
                    sh "ssh-keyscan -H ${ipAddress} >> ~/.ssh/known_hosts"
                }
            }
        }

        stage('Run Ansible Playbook') {
            steps {
                script {
                    // Change to the Ansible directory and run the playbook
                    dir('/tmp/IaC_with_Terraform/ansible') {
                        // Run the Ansible playbook without modifying ANSIBLE_SSH_ARGS
                        sh 'ansible-playbook -i inventory.ini web_server.yml'
                    }
                }
            }
        }
    }
}
