import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/configuration/app_configuration.dart';

void main() {
  test('development defaults to the local API prefix', () {
    final configuration = AppConfiguration.fromValues();
    expect(configuration.environment, AppEnvironment.development);
    expect(configuration.apiBaseUrl.toString(), 'http://localhost:8080/api/v1');
  });

  for (final environment in ['development', 'staging', 'production']) {
    test('$environment accepts an explicit API URL', () {
      final configuration = AppConfiguration.fromValues(
        environment: environment,
        apiBaseUrl: 'https://api.example.test/api/v1',
      );
      expect(configuration.environment.name, environment);
      expect(
        configuration.apiBaseUrl,
        Uri.parse('https://api.example.test/api/v1'),
      );
    });
  }

  test('compile-time defaults never select production or future providers', () {
    final configuration = AppConfiguration.fromEnvironment();
    expect(configuration.environment, AppEnvironment.development);
    expect(configuration.apiBaseUrl.toString(), 'http://localhost:8080/api/v1');
  });

  for (final environment in ['staging', 'production']) {
    test('$environment requires an explicit API URL', () {
      expect(
        () => AppConfiguration.fromValues(environment: environment),
        throwsA(
          isA<ArgumentError>().having(
            (error) => error.message,
            'message',
            contains('API_BASE_URL is required'),
          ),
        ),
      );
    });
    test('$environment rejects insecure API without a fallback', () {
      expect(
        () => AppConfiguration.fromValues(
          environment: environment,
          apiBaseUrl: 'http://api.example.test/api/v1',
        ),
        throwsA(
          isA<ArgumentError>().having(
            (error) => error.message,
            'message',
            contains('must use HTTPS'),
          ),
        ),
      );
    });
  }

  test('rejects unknown environments', () {
    expect(
      () => AppConfiguration.fromValues(environment: 'other'),
      throwsArgumentError,
    );
  });

  for (final url in [
    'not-a-url',
    '/api/v1',
    'ftp://example.test',
    'https://user:secret@example.test/api/v1',
    'https://example.test/api/v1?token=secret',
    'https://example.test/api/v1#fragment',
  ]) {
    test('rejects malformed or unsafe API URL: $url', () {
      expect(
        () => AppConfiguration.fromValues(apiBaseUrl: url),
        throwsArgumentError,
      );
    });
  }
}
