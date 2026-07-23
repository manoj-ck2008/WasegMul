# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.1.x   | :white_check_mark: |
| < 1.1   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability within WasegMul, please send an email to the project maintainer. All security vulnerabilities will be promptly addressed.

**Please do NOT report security vulnerabilities through public GitHub issues.**

### What to include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response timeline

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 1 week
- **Fix or mitigation**: Depends on severity

## Security Considerations

WasegMul is an offline-first application:

- **No network calls**: Classification runs entirely on-device using TensorFlow Lite
- **No data collection**: No analytics, telemetry, or user tracking
- **No cloud sync**: All data stays on the device
- **No API keys**: The app uses no external services requiring credentials
- **Camera**: Camera access is only used for live image classification and YOLO detection

## Best Practices for Deployment

- Do not bundle signing keys in source control
- Use `keystore.properties` for release signing (excluded from VCS)
- Enable R8/ProGuard minification for release builds (already configured)
- Review `network_security_config.xml` before production deployment
