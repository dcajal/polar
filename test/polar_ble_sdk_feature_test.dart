import 'package:flutter_test/flutter_test.dart';
import 'package:polar/polar.dart';

void main() {
  group('PolarSdkFeature.fromJson', () {
    test('parses FEATURE_POLAR_OFFLINE_EXERCISE_V2', () {
      expect(
        PolarSdkFeature.fromJson('FEATURE_POLAR_OFFLINE_EXERCISE_V2'),
        PolarSdkFeature.offlineExerciseV2,
      );
    });

    test('parses FEATURE_POLAR_TRAINING_DATA', () {
      expect(
        PolarSdkFeature.fromJson('FEATURE_POLAR_TRAINING_DATA'),
        PolarSdkFeature.trainingData,
      );
    });

    test('parses FEATURE_POLAR_DEVICE_CONTROL', () {
      expect(
        PolarSdkFeature.fromJson('FEATURE_POLAR_DEVICE_CONTROL'),
        PolarSdkFeature.deviceControl,
      );
    });
  });
}
