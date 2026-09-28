// Clash Mi Lite does not attach device or installation identifiers to URLs.
abstract final class AppUrlUtils {
  static String getQueryParamsForAnalytics(String bodyLen) => "";

  static Future<String> getQueryParamsForUrl({String bodyLen = "0"}) async =>
      "";
}
