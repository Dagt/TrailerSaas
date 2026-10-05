enum AppEnvironment { development, staging, production }

class AppConfiguration {
  const AppConfiguration._({
    required this.environment,
    required this.apiBaseUrl,
  });

  factory AppConfiguration.fromEnvironment() {
    return AppConfiguration.fromValues(
      environment: const String.fromEnvironment(
        'APP_ENV',
        defaultValue: 'development',
      ),
      apiBaseUrl: const String.fromEnvironment('API_BASE_URL'),
    );
  }

  factory AppConfiguration.fromValues({
    String environment = 'development',
    String apiBaseUrl = '',
  }) {
    final parsedEnvironment = AppEnvironment.values.where(
      (value) => value.name == environment,
    );
    if (parsedEnvironment.isEmpty) {
      throw ArgumentError(
        'APP_ENV must be development, staging or production.',
      );
    }
    final selectedEnvironment = parsedEnvironment.single;
    if (apiBaseUrl.isEmpty &&
        selectedEnvironment != AppEnvironment.development) {
      throw ArgumentError(
        'API_BASE_URL is required for staging and production.',
      );
    }
    final effectiveUrl =
        apiBaseUrl.isEmpty && selectedEnvironment == AppEnvironment.development
        ? 'http://localhost:8080/api/v1'
        : apiBaseUrl;
    final uri = Uri.tryParse(effectiveUrl);
    if (uri == null ||
        !uri.hasAuthority ||
        uri.host.isEmpty ||
        !const ['http', 'https'].contains(uri.scheme) ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment) {
      throw ArgumentError('API_BASE_URL must be an absolute HTTP(S) API URL.');
    }
    if (selectedEnvironment != AppEnvironment.development &&
        uri.scheme != 'https') {
      throw ArgumentError(
        'API_BASE_URL must use HTTPS for staging and production.',
      );
    }
    return AppConfiguration._(
      environment: selectedEnvironment,
      apiBaseUrl: uri,
    );
  }

  final AppEnvironment environment;
  final Uri apiBaseUrl;
}
