=== TFLite Model Placement ===

Copy your compiled model here as:
    app/src/main/assets/fog_model.tflite

Model spec
----------
  Input  : [1, 720]  int8   (120 samples × 6 ch, row-major flattened)
  Output0: [1, 3]    int8   (activity softmax — Other / Stationary / Walking)
  Output1: [1, 1]    int8   (FOG sigmoid probability)

Quantisation (applied in FogDetectionModel.kt)
  scale      = 1 / 256
  zero_point = -128
  float_val  = (raw_int8 - zero_point) * scale
             = (raw_int8 + 128) / 256

Activity index mapping
  0 = Other
  1 = Stationary  (Lying / Sitting / Standing)
  2 = Walking
