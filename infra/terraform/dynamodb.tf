resource "aws_dynamodb_table" "incidents" {
  name         = "${var.environment}-incidents"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "incidentId"

  attribute {
    name = "incidentId"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_dynamodb_table" "metrics_state" {
  name         = "${var.environment}-metrics-state"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "jobType"
  range_key    = "bucket"

  attribute {
    name = "jobType"
    type = "S"
  }

  attribute {
    name = "bucket"
    type = "S"
  }

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
