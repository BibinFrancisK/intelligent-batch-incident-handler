output "ec2_public_ip" {
  description = "Public IP of the EC2 instance running the incident intelligence service."
  value       = aws_instance.app_server.public_ip
}

output "ec2_public_dns" {
  description = "Public DNS of the EC2 instance."
  value       = aws_instance.app_server.public_dns
}

output "incidents_table_name" {
  description = "Name of the incidents DynamoDB table."
  value       = aws_dynamodb_table.incidents.name
}

output "metrics_state_table_name" {
  description = "Name of the metrics_state DynamoDB table."
  value       = aws_dynamodb_table.metrics_state.name
}
