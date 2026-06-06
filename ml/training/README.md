# FOG And Activity Model Training

This project trains a dual-task deep learning model for:

- human activity recognition
- Freezing of Gait (FOG) detection

The main workflow lives in the notebook [FOG_Activitytraining_2_head.ipynb](/mnt/c/Users/KIIT0001/Downloads/model%20training-20260329T170051Z-1-001/model%20training/FOG_Activitytraining_2_head.ipynb), and the folder also contains exported model files:

- [fog_activity.keras](/mnt/c/Users/KIIT0001/Downloads/model%20training-20260329T170051Z-1-001/model%20training/fog_activity.keras)
- [fog_model.tflite](/mnt/c/Users/KIIT0001/Downloads/model%20training-20260329T170051Z-1-001/model%20training/fog_model.tflite)

## Project Goal

The notebook builds a single model with two outputs from the same IMU window input:

- an activity class prediction
- a binary FOG prediction

The design explicitly prioritizes activity quality while still optimizing FOG detection.

## Input Data

The notebook expects a folder of CSV files at:

`/content/drive/MyDrive/Colab Notebooks/csv_activity_fog`

This path is set for Google Colab and is not included in this project folder, so anyone re-running the notebook will need to update `CSV_DIR` to match their own dataset location.

### Expected CSV columns

Each CSV must contain:

- `window_id`
- `subject`
- `acc_x`
- `acc_y`
- `acc_z`
- `gyro_x`
- `gyro_y`
- `gyro_z`
- `activity`
- `label`

Files missing these columns are skipped.

## Window Format

The model uses fixed IMU windows with:

- window length: `120`
- channels: `6`

Each window is built from:

1. `acc_x`
2. `acc_y`
3. `acc_z`
4. `gyro_x`
5. `gyro_y`
6. `gyro_z`

Each accepted `window_id` group must have exactly `120` rows.

So the model input shape is:

`(120, 6)`

## Labels

The notebook trains on two label targets.

### 1. Activity head

Raw activity strings are first mapped into detailed IDs, then reduced into 4 final classes:

- `0 = Other`
- `1 = Stationary`
- `2 = Walking`
- `3 = Shuffling`

The notebook comments highlight an important fix:

- `Shuffling` is treated as its own final class instead of being merged into `Other`

### 2. FOG head

The binary FOG label is taken from the CSV column:

- `label`

Expected meaning:

- `0 = No FOG`
- `1 = FOG`

## Data Loading And Splitting

The notebook loads windows from all CSV files and tracks a subject ID for each window.

It then performs a subject-based split using `GroupShuffleSplit`:

- `60%` train
- `20%` validation
- `20%` test

This is a key part of the design because it prevents the same subject from appearing in multiple splits.

The notebook also asserts that:

- train/validation subjects do not overlap
- train/test subjects do not overlap
- validation/test subjects do not overlap

## Preprocessing

The IMU channels are standardized using `StandardScaler`.

Important details:

- the scaler is fit only on the training set
- validation and test data are transformed using the training scaler
- normalization is performed per channel

After preprocessing, the training data should have mean near `0` and standard deviation near `1`.

## Model Architecture

The notebook defines a dual-head neural network with shared temporal feature extraction and task-specific output branches.

### Shared backbone

The shared part includes:

- multiscale `Conv1D` branches with kernel sizes `3`, `7`, and `13`
- max pooling
- two residual `Conv1D` blocks
- more max pooling
- two bidirectional `GRU` layers

### Task-specific heads

After the shared temporal backbone, the model splits into two separate attention-based heads:

- activity head with its own `TemporalAttention`
- FOG head with its own `TemporalAttention`

This is important because each task can focus on different timesteps in the same input window.

### Activity output

The activity branch ends with:

- dense layers
- dropout
- softmax output

### FOG output

The FOG branch ends with:

- dense layers
- dropout
- sigmoid output

## Losses And Training Strategy

The notebook uses focal loss for both tasks.

### Activity loss

- multiclass focal loss
- class-dependent alpha values
- extra minority-class boost
- gamma set to `3.0`

### FOG loss

- binary focal loss
- positive-class weighting based on class imbalance
- gamma set to `2.0`

### Loss balance

The two tasks are given equal loss weights:

- activity: `2.5`
- FOG: `2.5`

Even though the notebook describes this as an activity-priority pipeline, that priority is mainly expressed in the validation metric used for training control.

## Sample Weighting

The notebook computes balanced per-sample weights for both tasks using class-frequency-based weights from the training set.

This is done so minority classes contribute more evenly during optimization.

## Validation Metric Used For Early Stopping

Training does not stop based on a single raw loss alone.

The notebook defines a custom combined validation score:

`val_combined = 0.6 * activity_macro_f1 + 0.4 * fog_f2`

This means:

- activity macro-F1 contributes `60%`
- FOG F-beta contributes `40%`

The notebook uses this combined metric for:

- early stopping
- learning-rate reduction

This is the main reason the notebook describes the pipeline as activity-priority.

## Training Configuration

Key settings from the notebook:

- batch size: `64`
- epochs: `60`
- optimizer: `Adam(1e-4)`
- early stopping patience: `8`
- reduce LR patience: `4`

The notebook is written for Google Colab and recommends a `T4 GPU`.

If a GPU is available, it enables:

- TensorFlow mixed precision with `mixed_float16`

## Threshold Tuning For FOG

After training, the notebook tunes the FOG decision threshold on the validation set rather than using a fixed threshold of `0.5`.

The logic is:

- find the threshold that optimizes an F-beta style objective on validation predictions
- prefer high recall for FOG
- apply fallback rules if precision and recall floors are not both satisfied

Threshold-related settings in the notebook:

- recall floor: `0.90`
- precision floor: `0.70`

This tuned threshold is then used for final test-set evaluation.

## Evaluation Outputs

The notebook evaluates both tasks on the held-out test set and produces:

- activity classification report
- FOG classification report
- activity confusion matrix
- FOG confusion matrix
- FOG ROC curve
- training and validation metric plots

## Saved Artifacts

The notebook saves:

- `fog_activity.keras`
- `fog_activity.tflite`

Important note:

- the notebook code saves the TFLite export as `fog_activity.tflite`
- this folder currently contains `fog_model.tflite`

That likely means one of these is true:

- the exported TFLite file was renamed after training
- the notebook was run in a slightly different version when this folder was prepared

Anyone using this project should verify which TFLite file is the intended deployment artifact.

## TFLite Conversion Details

The notebook converts the saved Keras model using `TFLiteConverter` with:

- built-in TFLite ops
- `SELECT_TF_OPS`
- default optimization
- float16 supported types

This is done because the model uses GRU-based sequence layers and custom behavior that may require Flex ops support.

If conversion fails, the notebook attempts a fallback conversion path.

## Dependencies

The notebook installs and uses:

- TensorFlow `>= 2.15.0`
- scikit-learn `>= 1.3.0`
- NumPy `>= 1.24.0`
- pandas `>= 2.0.0`
- matplotlib `>= 3.7.0`
- seaborn `>= 0.12.0`

## How To Run

1. Open the notebook in Google Colab or Jupyter.
2. Update `CSV_DIR` so it points to your dataset folder.
3. Make sure the CSV files match the required schema.
4. Run the cells in order.
5. Review the validation metrics and test reports.
6. Use the saved `.keras` or `.tflite` artifact for deployment.

## Limitations And Notes

- The dataset itself is not included in this folder.
- The notebook assumes pre-windowed CSV data with `window_id`.
- Reproducing the exact saved artifacts depends on using the same dataset and notebook version.
- The exported TFLite model may require TensorFlow Lite Flex / Select TF Ops support because of the model architecture.

## File Structure

```text
model training/
|-- FOG_Activitytraining_2_head.ipynb
|-- fog_activity.keras
|-- fog_model.tflite
`-- README.md
```
