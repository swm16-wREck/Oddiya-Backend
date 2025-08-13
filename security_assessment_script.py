#!/usr/bin/env python3
"""
Comprehensive Security Vulnerability Assessment Script for Oddiya Application
Tests for OWASP Top 10 vulnerabilities and common security issues
"""

import requests
import json
import re
import sys
import time
from urllib.parse import urljoin, urlparse
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
import subprocess
import socket
from datetime import datetime

class SecurityScanner:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        
        # Configure retry strategy
        retry_strategy = Retry(
            total=3,
            status_forcelist=[429, 500, 502, 503, 504],
            allowed_methods=["HEAD", "GET", "OPTIONS", "POST"]
        )
        adapter = HTTPAdapter(max_retries=retry_strategy)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)
        
        self.vulnerabilities = []
        self.report = {
            "timestamp": datetime.now().isoformat(),
            "target": base_url,
            "findings": []
        }
        
    def log_finding(self, severity, category, title, description, evidence=None, recommendation=None):
        """Log security finding"""
        finding = {
            "severity": severity,
            "category": category,
            "title": title,
            "description": description,
            "evidence": evidence or "",
            "recommendation": recommendation or ""
        }
        self.report["findings"].append(finding)
        print(f"[{severity}] {category}: {title}")
        if evidence:
            print(f"  Evidence: {evidence}")
        
    def test_ssl_tls_configuration(self):
        """Test SSL/TLS configuration"""
        print("\n=== SSL/TLS Configuration Testing ===")
        
        # Check if HTTPS is available
        try:
            response = self.session.get(self.base_url.replace('http://', 'https://'), timeout=10, verify=False)
            self.log_finding("HIGH", "SSL/TLS", "HTTPS Available", "HTTPS endpoint is accessible")
        except requests.exceptions.ConnectionError:
            self.log_finding("CRITICAL", "SSL/TLS", "HTTPS Not Available", 
                           "No HTTPS endpoint found. All traffic is transmitted over unencrypted HTTP.",
                           evidence="Connection refused on port 443",
                           recommendation="Configure SSL/TLS certificate and enable HTTPS")
        except Exception as e:
            self.log_finding("HIGH", "SSL/TLS", "HTTPS Connection Issues", f"Error connecting to HTTPS endpoint: {str(e)}")
            
        # Check HTTP Strict Transport Security (HSTS)
        try:
            response = self.session.get(self.base_url, timeout=10)
            if 'Strict-Transport-Security' not in response.headers:
                self.log_finding("MEDIUM", "SSL/TLS", "Missing HSTS Header", 
                               "HTTP Strict Transport Security header not present",
                               recommendation="Add HSTS header to force HTTPS connections")
        except Exception as e:
            print(f"Error testing HSTS: {e}")
    
    def test_authentication_bypass(self):
        """Test authentication bypass vulnerabilities"""
        print("\n=== Authentication System Testing ===")
        
        # Test endpoints that should require authentication
        protected_endpoints = [
            '/api/v1/users/profile',
            '/api/v1/travel-plans',
            '/api/v1/travel-plans/create',
            '/api/v1/places/favorites',
            '/api/v1/files/upload',
            '/actuator'
        ]
        
        for endpoint in protected_endpoints:
            try:
                url = urljoin(self.base_url, endpoint)
                response = self.session.get(url, timeout=10)
                
                if response.status_code == 200:
                    self.log_finding("HIGH", "Authentication", "Authentication Bypass", 
                                   f"Protected endpoint accessible without authentication: {endpoint}",
                                   evidence=f"Status: {response.status_code}, Response length: {len(response.text)}")
                elif response.status_code == 401:
                    print(f"  ✓ {endpoint} properly protected (401 Unauthorized)")
                elif response.status_code == 403:
                    print(f"  ✓ {endpoint} properly protected (403 Forbidden)")
                else:
                    print(f"  ? {endpoint} returned {response.status_code}")
                    
            except Exception as e:
                print(f"Error testing {endpoint}: {e}")
    
    def test_jwt_vulnerabilities(self):
        """Test JWT-related vulnerabilities"""
        print("\n=== JWT Security Testing ===")
        
        # Test with malformed JWT tokens
        malformed_tokens = [
            "invalid.jwt.token",
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJub25lIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.",
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTUxNjIzOTAyMn0.invalid",
            ""
        ]
        
        for token in malformed_tokens:
            try:
                headers = {"Authorization": f"Bearer {token}"}
                url = urljoin(self.base_url, '/api/v1/users/profile')
                response = self.session.get(url, headers=headers, timeout=10)
                
                if response.status_code == 200:
                    self.log_finding("CRITICAL", "Authentication", "JWT Bypass Vulnerability", 
                                   f"Malformed JWT token accepted: {token[:20]}...",
                                   evidence=f"Token accepted with status {response.status_code}")
            except Exception as e:
                print(f"Error testing JWT token: {e}")
    
    def test_sql_injection(self):
        """Test SQL injection vulnerabilities"""
        print("\n=== SQL Injection Testing ===")
        
        # SQL injection payloads
        sql_payloads = [
            "' OR '1'='1",
            "' OR '1'='1' --",
            "' OR '1'='1' /*",
            "admin'--",
            "admin' #",
            "admin'/*",
            "' OR 1=1--",
            "' OR 1=1#",
            "' OR 1=1/*",
            "'; DROP TABLE users;--",
            "1' UNION SELECT null,version(),null--"
        ]
        
        # Test search endpoints
        search_endpoints = [
            '/api/v1/places/search',
            '/api/v1/places/nearby'
        ]
        
        for endpoint in search_endpoints:
            for payload in sql_payloads[:5]:  # Test first 5 payloads to avoid overwhelming
                try:
                    url = urljoin(self.base_url, endpoint)
                    params = {'q': payload, 'query': payload, 'search': payload}
                    
                    response = self.session.get(url, params=params, timeout=10)
                    
                    # Check for SQL error messages
                    sql_errors = [
                        "sql syntax",
                        "mysql_fetch",
                        "ora-01756",
                        "microsoft odbc",
                        "postgresql",
                        "warning: mysql",
                        "sqlite_master",
                        "syntax error",
                        "unclosed quotation mark"
                    ]
                    
                    response_lower = response.text.lower()
                    for error in sql_errors:
                        if error in response_lower:
                            self.log_finding("CRITICAL", "Injection", "SQL Injection Vulnerability", 
                                           f"SQL error detected in endpoint {endpoint}",
                                           evidence=f"Payload: {payload}, Error pattern: {error}",
                                           recommendation="Use parameterized queries and input validation")
                            break
                            
                except Exception as e:
                    print(f"Error testing SQL injection on {endpoint}: {e}")
    
    def test_xss_vulnerabilities(self):
        """Test Cross-Site Scripting vulnerabilities"""
        print("\n=== XSS Vulnerability Testing ===")
        
        xss_payloads = [
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "javascript:alert('XSS')",
            "<svg onload=alert('XSS')>",
            "'-alert('XSS')-'",
            "\"><script>alert('XSS')</script>",
            "<iframe src=javascript:alert('XSS')></iframe>"
        ]
        
        # Test input endpoints
        test_endpoints = [
            ('/api/v1/places/search', {'q': '', 'query': ''}),
            ('/api/v1/auth/login', {'username': '', 'password': ''}),
        ]
        
        for endpoint, params in test_endpoints:
            for payload in xss_payloads[:3]:  # Test first 3 payloads
                try:
                    url = urljoin(self.base_url, endpoint)
                    test_params = {key: payload for key in params.keys()}
                    
                    if endpoint == '/api/v1/auth/login':
                        response = self.session.post(url, json=test_params, timeout=10)
                    else:
                        response = self.session.get(url, params=test_params, timeout=10)
                    
                    # Check if payload is reflected in response
                    if payload in response.text:
                        self.log_finding("HIGH", "XSS", "Reflected XSS Vulnerability", 
                                       f"XSS payload reflected in {endpoint}",
                                       evidence=f"Payload: {payload[:30]}... found in response",
                                       recommendation="Implement proper input validation and output encoding")
                        
                except Exception as e:
                    print(f"Error testing XSS on {endpoint}: {e}")
    
    def test_cors_configuration(self):
        """Test CORS configuration security"""
        print("\n=== CORS Configuration Testing ===")
        
        try:
            headers = {
                'Origin': 'https://evil.com',
                'Access-Control-Request-Method': 'GET',
                'Access-Control-Request-Headers': 'authorization'
            }
            
            response = self.session.options(self.base_url, headers=headers, timeout=10)
            
            cors_headers = {
                'Access-Control-Allow-Origin': response.headers.get('Access-Control-Allow-Origin'),
                'Access-Control-Allow-Credentials': response.headers.get('Access-Control-Allow-Credentials'),
                'Access-Control-Allow-Methods': response.headers.get('Access-Control-Allow-Methods'),
                'Access-Control-Allow-Headers': response.headers.get('Access-Control-Allow-Headers')
            }
            
            # Check for overly permissive CORS
            if cors_headers.get('Access-Control-Allow-Origin') == '*' and cors_headers.get('Access-Control-Allow-Credentials') == 'true':
                self.log_finding("HIGH", "CORS", "Insecure CORS Configuration", 
                               "CORS allows any origin with credentials",
                               evidence=f"Allow-Origin: *, Allow-Credentials: true",
                               recommendation="Restrict CORS to specific trusted domains")
            
            if cors_headers.get('Access-Control-Allow-Origin') == 'https://evil.com':
                self.log_finding("CRITICAL", "CORS", "CORS Origin Validation Bypass", 
                               "CORS accepts arbitrary origins",
                               evidence="Evil origin accepted by CORS policy")
                               
        except Exception as e:
            print(f"Error testing CORS: {e}")
    
    def test_information_disclosure(self):
        """Test for information disclosure vulnerabilities"""
        print("\n=== Information Disclosure Testing ===")
        
        # Test for sensitive information in responses
        test_urls = [
            '/',
            '/api/v1/health',
            '/actuator',
            '/actuator/health',
            '/actuator/info',
            '/actuator/env',
            '/actuator/configprops',
            '/swagger-ui.html',
            '/api-docs',
            '/error'
        ]
        
        sensitive_patterns = [
            r'password\s*[:=]\s*["\']([^"\']+)["\']',
            r'secret\s*[:=]\s*["\']([^"\']+)["\']',
            r'token\s*[:=]\s*["\']([^"\']+)["\']',
            r'key\s*[:=]\s*["\']([^"\']+)["\']',
            r'aws.*[:=]\s*["\']([^"\']+)["\']',
            r'database.*[:=]\s*["\']([^"\']+)["\']',
            r'jdbc:.*://.*:[0-9]+',
            r'[A-Za-z0-9+/]{20,}={0,2}',  # Base64 encoded strings
        ]
        
        for url in test_urls:
            try:
                full_url = urljoin(self.base_url, url)
                response = self.session.get(full_url, timeout=10)
                
                # Check for sensitive information patterns
                for pattern in sensitive_patterns:
                    matches = re.findall(pattern, response.text, re.IGNORECASE)
                    if matches:
                        self.log_finding("MEDIUM", "Information Disclosure", f"Sensitive Information Exposed in {url}", 
                                       f"Potential sensitive data found in response",
                                       evidence=f"Pattern matched: {pattern[:30]}...",
                                       recommendation="Remove sensitive information from public endpoints")
                
                # Check for detailed error messages
                if 'Exception' in response.text or 'Stacktrace' in response.text or 'java.lang' in response.text:
                    self.log_finding("LOW", "Information Disclosure", f"Detailed Error Messages in {url}", 
                                   "Detailed error messages may reveal system information",
                                   recommendation="Implement generic error messages for production")
                    
            except Exception as e:
                print(f"Error testing information disclosure on {url}: {e}")
    
    def test_admin_endpoints(self):
        """Test for exposed admin endpoints"""
        print("\n=== Admin Endpoint Discovery ===")
        
        admin_endpoints = [
            '/admin',
            '/admin/',
            '/admin/login',
            '/admin/dashboard',
            '/management',
            '/console',
            '/actuator',
            '/actuator/shutdown',
            '/actuator/env',
            '/actuator/configprops',
            '/actuator/loggers',
            '/actuator/heapdump',
            '/actuator/threaddump',
            '/h2-console',
            '/swagger-ui',
            '/swagger-ui.html'
        ]
        
        for endpoint in admin_endpoints:
            try:
                url = urljoin(self.base_url, endpoint)
                response = self.session.get(url, timeout=10)
                
                if response.status_code == 200:
                    self.log_finding("MEDIUM", "Access Control", f"Admin Endpoint Accessible: {endpoint}", 
                                   f"Admin endpoint returns HTTP 200",
                                   evidence=f"Content-Length: {len(response.text)}",
                                   recommendation="Restrict access to admin endpoints or disable in production")
                elif response.status_code in [301, 302]:
                    self.log_finding("LOW", "Access Control", f"Admin Endpoint Redirects: {endpoint}", 
                                   f"Admin endpoint redirects (may be accessible)",
                                   evidence=f"Location: {response.headers.get('Location', 'N/A')}")
                    
            except Exception as e:
                print(f"Error testing admin endpoint {endpoint}: {e}")
    
    def test_http_security_headers(self):
        """Test HTTP security headers"""
        print("\n=== HTTP Security Headers Testing ===")
        
        try:
            response = self.session.get(self.base_url, timeout=10)
            headers = response.headers
            
            security_headers = {
                'X-Content-Type-Options': 'nosniff',
                'X-Frame-Options': ['DENY', 'SAMEORIGIN'],
                'X-XSS-Protection': '1; mode=block',
                'Strict-Transport-Security': None,  # Should exist
                'Content-Security-Policy': None,     # Should exist
                'Referrer-Policy': None,             # Should exist
            }
            
            for header, expected in security_headers.items():
                if header not in headers:
                    self.log_finding("MEDIUM", "HTTP Security", f"Missing Security Header: {header}", 
                                   f"Security header {header} not present",
                                   recommendation=f"Add {header} header for enhanced security")
                elif expected and isinstance(expected, list) and headers[header] not in expected:
                    self.log_finding("LOW", "HTTP Security", f"Suboptimal Security Header: {header}", 
                                   f"Header value: {headers[header]} may not be optimal")
                    
        except Exception as e:
            print(f"Error testing security headers: {e}")
    
    def test_owasp_top_10(self):
        """Comprehensive OWASP Top 10 testing"""
        print("\n=== OWASP Top 10 Comprehensive Testing ===")
        
        # A01:2021 – Broken Access Control
        self.test_authentication_bypass()
        self.test_admin_endpoints()
        
        # A02:2021 – Cryptographic Failures
        self.test_ssl_tls_configuration()
        
        # A03:2021 – Injection
        self.test_sql_injection()
        self.test_xss_vulnerabilities()
        
        # A05:2021 – Security Misconfiguration
        self.test_information_disclosure()
        self.test_http_security_headers()
        
        # A07:2021 – Identification and Authentication Failures
        self.test_jwt_vulnerabilities()
        
    def generate_report(self):
        """Generate comprehensive security report"""
        print("\n" + "="*60)
        print("SECURITY ASSESSMENT REPORT")
        print("="*60)
        print(f"Target: {self.base_url}")
        print(f"Timestamp: {self.report['timestamp']}")
        print(f"Total Findings: {len(self.report['findings'])}")
        
        # Categorize findings by severity
        severity_counts = {}
        for finding in self.report["findings"]:
            severity = finding["severity"]
            severity_counts[severity] = severity_counts.get(severity, 0) + 1
        
        print("\nFINDINGS SUMMARY:")
        for severity in ["CRITICAL", "HIGH", "MEDIUM", "LOW"]:
            count = severity_counts.get(severity, 0)
            print(f"  {severity}: {count}")
        
        print("\nDETAILED FINDINGS:")
        for i, finding in enumerate(self.report["findings"], 1):
            print(f"\n{i}. [{finding['severity']}] {finding['title']}")
            print(f"   Category: {finding['category']}")
            print(f"   Description: {finding['description']}")
            if finding['evidence']:
                print(f"   Evidence: {finding['evidence']}")
            if finding['recommendation']:
                print(f"   Recommendation: {finding['recommendation']}")
        
        # Save report to file
        report_file = f"security_assessment_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(report_file, 'w') as f:
            json.dump(self.report, f, indent=2)
        print(f"\nDetailed report saved to: {report_file}")
        
        return self.report

def main():
    target_url = "http://oddiya-dev-alb-1801802442.ap-northeast-2.elb.amazonaws.com"
    
    print("="*60)
    print("ODDIYA APPLICATION SECURITY ASSESSMENT")
    print("="*60)
    print(f"Target: {target_url}")
    print(f"Started: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    
    scanner = SecurityScanner(target_url)
    
    try:
        # Run comprehensive security tests
        scanner.test_ssl_tls_configuration()
        scanner.test_authentication_bypass()
        scanner.test_jwt_vulnerabilities()
        scanner.test_sql_injection()
        scanner.test_xss_vulnerabilities()
        scanner.test_cors_configuration()
        scanner.test_information_disclosure()
        scanner.test_admin_endpoints()
        scanner.test_http_security_headers()
        
        # Generate final report
        scanner.generate_report()
        
    except KeyboardInterrupt:
        print("\nScan interrupted by user")
    except Exception as e:
        print(f"\nError during scan: {e}")
    
    print(f"\nCompleted: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

if __name__ == "__main__":
    main()