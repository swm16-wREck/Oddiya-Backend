import json
import logging

# Set up logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):
    """
    Simple Lambda function for testing AWS integration.
    This function can be used to test Lambda triggers from S3, SQS, etc.
    """
    logger.info(f"Received event: {json.dumps(event)}")
    
    # Extract event source
    event_source = None
    if 'Records' in event:
        if event['Records']:
            record = event['Records'][0]
            if 'eventSource' in record:
                event_source = record['eventSource']
            elif 's3' in record:
                event_source = 'aws:s3'
            elif 'Sns' in record:
                event_source = 'aws:sns'
            elif 'body' in record:  # SQS message
                event_source = 'aws:sqs'
    
    # Process based on event source
    response = {
        'statusCode': 200,
        'headers': {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*'
        },
        'body': {
            'message': 'Lambda function executed successfully',
            'event_source': event_source,
            'timestamp': context.aws_request_id if context else 'test-request-id',
            'function_name': context.function_name if context else 'oddiya-test-function',
            'processed_records': len(event.get('Records', []))
        }
    }
    
    # Handle S3 events
    if event_source == 'aws:s3':
        s3_records = []
        for record in event.get('Records', []):
            if 's3' in record:
                s3_info = {
                    'bucket': record['s3']['bucket']['name'],
                    'key': record['s3']['object']['key'],
                    'event_name': record.get('eventName', 'unknown'),
                    'size': record['s3']['object'].get('size', 0)
                }
                s3_records.append(s3_info)
        
        response['body']['s3_records'] = s3_records
        logger.info(f"Processed {len(s3_records)} S3 records")
    
    # Handle SQS events
    elif event_source == 'aws:sqs':
        sqs_messages = []
        for record in event.get('Records', []):
            if 'body' in record:
                try:
                    message_body = json.loads(record['body'])
                except:
                    message_body = record['body']
                
                sqs_info = {
                    'message_id': record.get('messageId', 'unknown'),
                    'body': message_body,
                    'attributes': record.get('attributes', {}),
                    'receipt_handle': record.get('receiptHandle', '')
                }
                sqs_messages.append(sqs_info)
        
        response['body']['sqs_messages'] = sqs_messages
        logger.info(f"Processed {len(sqs_messages)} SQS messages")
    
    # Handle direct invocation or other events
    else:
        response['body']['direct_invocation'] = True
        response['body']['event_data'] = event
        logger.info("Direct Lambda invocation")
    
    # Return JSON response
    return {
        'statusCode': response['statusCode'],
        'headers': response['headers'],
        'body': json.dumps(response['body'])
    }

# For testing locally
if __name__ == '__main__':
    # Test with S3 event
    test_s3_event = {
        "Records": [
            {
                "eventVersion": "2.1",
                "eventSource": "aws:s3",
                "eventName": "ObjectCreated:Put",
                "s3": {
                    "bucket": {
                        "name": "oddiya-test-bucket"
                    },
                    "object": {
                        "key": "test/test-file.txt",
                        "size": 1024
                    }
                }
            }
        ]
    }
    
    # Mock context
    class MockContext:
        def __init__(self):
            self.aws_request_id = 'test-request-123'
            self.function_name = 'oddiya-test-function'
    
    result = lambda_handler(test_s3_event, MockContext())
    print(json.dumps(result, indent=2))