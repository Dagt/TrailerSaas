import 'dart:async';

import 'package:flutter/foundation.dart';

import '../platform/location_assistance.dart';
import 'geofence_evaluator.dart';

class ArrivalSuggestion {
  const ArrivalSuggestion(this.target, this.fix);
  final ArrivalTarget target;
  final AssistanceFix fix;
}

/// Ephemeral evidence only. This class has no API client, database or command sender.
class ArrivalAssistanceController extends ChangeNotifier {
  ArrivalAssistanceController(this.location, {DateTime Function()? clock})
    : now = clock ?? (() => DateTime.now().toUtc());
  final LocationAssistancePort location;
  final DateTime Function() now;
  ArrivalTarget? target;
  ArrivalSuggestion? _suggestion;
  String? _dismissed, message;
  bool foreground = true, busy = false, _disposed = false;
  int _generation = 0;
  Timer? _expiry;
  ArrivalSuggestion? get suggestion {
    final s = _suggestion;
    return s != null &&
            foreground &&
            target?.identity == s.target.identity &&
            GeofenceEvaluator.probable(s.target, s.fix, now())
        ? s
        : null;
  }

  bool get available =>
      !_disposed &&
      foreground &&
      target != null &&
      _dismissed != target!.identity;

  void update(ArrivalTarget? next) {
    if (target?.identity == next?.identity) return;
    invalidate();
    target = next;
    _dismissed = null;
    message = null;
  }

  void invalidate() {
    ++_generation;
    location.cancel();
    _expiry?.cancel();
    _expiry = null;
    _suggestion = null;
    busy = false;
    if (!_disposed) notifyListeners();
  }

  void setForeground(bool value) {
    foreground = value;
    invalidate();
  }

  void notYet() {
    _dismissed = target?.identity;
    invalidate();
  }

  Future<void> check() async {
    if (!available || busy) return;
    final context = target!, generation = ++_generation;
    bool current() =>
        !_disposed &&
        foreground &&
        generation == _generation &&
        target?.identity == context.identity;
    _suggestion = null;
    _expiry?.cancel();
    _expiry = null;
    busy = true;
    message = null;
    notifyListeners();
    try {
      final access = await location.access(request: true);
      if (!current()) return;
      if (!access.usable) {
        message = access.approximate
            ? 'Approximate location cannot suggest arrival. Use Mark Arrived.'
            : 'Location assistance is unavailable. You can still Mark Arrived manually.';
        return;
      }
      final fix = await location.read();
      if (!current()) return;
      final recheck = await location.access();
      if (!current()) return;
      if (fix != null &&
          recheck.usable &&
          GeofenceEvaluator.probable(context, fix, now())) {
        _suggestion = ArrivalSuggestion(context, fix);
        _expiry = Timer(
          GeofenceEvaluator.maxAge - now().difference(fix.capturedAt),
          invalidate,
        );
      } else {
        message = 'No reliable arrival suggestion. Mark Arrived manually or check again.';
      }
    } catch (_) {
      if (current()) {
        message = 'Location could not be read. Mark Arrived remains available.';
      }
    } finally {
      if (current()) {
        busy = false;
        notifyListeners();
      }
    }
  }

  Future<ArrivalSuggestion?> confirmedEvidence() async {
    final candidate = suggestion, generation = _generation;
    if (candidate == null) {
      invalidate();
      return null;
    }
    try {
      final access = await location.access();
      if (_disposed || generation != _generation) return null;
      if (!access.usable || !identical(suggestion, candidate)) {
        invalidate();
        return null;
      }
      invalidate();
      return candidate;
    } catch (_) {
      if (!_disposed && generation == _generation) invalidate();
      return null;
    }
  }

  @override
  void dispose() {
    _disposed = true;
    invalidate();
    target = null;
    super.dispose();
  }
}
