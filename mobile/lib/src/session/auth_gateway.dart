import 'package:firebase_auth/firebase_auth.dart';

/// Identity only. Custom claims are deliberately not read.
abstract interface class AuthGateway {
  String? get uid;
  Stream<String?> get changes;
  Future<void> signIn(String email, String password);
  Future<String?> token({bool forceRefresh = false});
  Future<void> signOut();
}

class FirebaseAuthGateway implements AuthGateway {
  FirebaseAuthGateway(this.auth);
  final FirebaseAuth auth;
  @override
  String? get uid => auth.currentUser?.uid;
  @override
  Stream<String?> get changes =>
      auth.authStateChanges().map((user) => user?.uid);
  @override
  Future<void> signIn(String email, String password) async {
    await auth.signInWithEmailAndPassword(email: email, password: password);
  }

  @override
  Future<String?> token({bool forceRefresh = false}) async =>
      auth.currentUser?.getIdToken(forceRefresh);
  @override
  Future<void> signOut() => auth.signOut();
}
