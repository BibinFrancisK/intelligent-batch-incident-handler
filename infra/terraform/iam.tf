data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2_app_role" {
  name               = "${var.environment}-incident-ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_iam_instance_profile" "ec2_app_profile" {
  name = "${var.environment}-incident-ec2-profile"
  role = aws_iam_role.ec2_app_role.name
}

data "aws_iam_policy_document" "ec2_dynamodb" {
  statement {
    effect = "Allow"
    actions = [
      "dynamodb:PutItem",
      "dynamodb:GetItem",
      "dynamodb:UpdateItem",
      "dynamodb:Query",
      "dynamodb:Scan",
    ]
    resources = [
      aws_dynamodb_table.incidents.arn,
      aws_dynamodb_table.metrics_state.arn,
    ]
  }
}

resource "aws_iam_policy" "ec2_dynamodb_policy" {
  name        = "${var.environment}-incident-ec2-dynamodb"
  description = "Grants the app EC2 instance read/write access to incidents and metrics_state tables."
  policy      = data.aws_iam_policy_document.ec2_dynamodb.json
}

resource "aws_iam_role_policy_attachment" "ec2_dynamodb" {
  role       = aws_iam_role.ec2_app_role.name
  policy_arn = aws_iam_policy.ec2_dynamodb_policy.arn
}
