# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in WasegMul, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

Instead, please email the maintainers directly or use [GitHub's private vulnerability reporting](https://github.com/manoj-ck2008/WasegMul/security/advisories/new).

## What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

## Response Timeline

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 1 week
- **Fix or mitigation**: Depends on severity

## Security Considerations

### On-Device Processing

WasegMul processes all images **entirely on-device**. No image data is sent to external servers. This is a core privacy guarantee.

### Data Storage

- Classification history is stored locally in a Room database
- No cloud sync or remote storage is implemented
- Data backup is disabled via `android:allowBackup="false"` and XML backup rules

### Model Security

- TFLite models are bundled in the APK assets
- No model downloads at runtime
- No network calls are made during classification

### Build Security

- Release builds require a signing keystore (`keystore.properties`)
- Release signing credentials are never committed to version control
- Network security config blocks cleartext traffic

## Scope

This security policy applies to the latest release of WasegMul on the `main` branch.
