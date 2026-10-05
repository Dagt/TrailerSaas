/// Future implementations capture into app-private storage; cancellation is null.
abstract interface class Camera {
  Future<Uri?> capturePhoto();
}
