# Smartwatch Stream App

Tested on Galaxy Watch 5.

A WearOS app to stream sensor data to a target IP. Alternatively, the app can record smartwatch
sensor data and store it as a csv.
The app stores recorded data to:

```
storage/emulated/0/Documents/{date-time}.csv
```

## Install and Use

Please read
the [step-by-step instructions](https://docs.google.com/document/d/1ayMBF9kDCB9rlcrqR0sPumJhIVJgOF-SENTdoE4a6DI/edit?usp=sharing)

## Recorded and streamed data

The data arrays are composed in the `GlobalState.kt` file. Currently, streamed sensor messages
consist of:

```
    time_diff + // time since streaming started (not used for predicitons)
    rotVec +  // rotation vector[5]  is a quaternion x,y,z,w, + confidence
    lacc + // [3] linear acceleration x,y,z
    pres +  // [1] atmospheric pressure
    grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
    gyro + // [3] gyro data for time series prediction
    hrRaw + // [16] undocumented data from Samsung's Hr raw sensor (not used for predictions)
    init_pres + // initial atmospheric pressure collected during calibration
    north_deg // body orientation in relation to magnetic north pole collected during calibration
                                
```

Recorded sensor data consists of

```
    time_diff + // time since streaming started (not used for predicitons)
    rotVec + // rotation vector[5]  is a quaternion x,y,z,w, + confidence
    lacc + // [3] linear acceleration x,y,z
    accl + // [3] unfiltered acceleration x,y,z
    pres +  // [1] atmospheric pressure
    gyro + // [3] Angular speed around the x,y,z -axis
    magn + // [3] the ambient magnetic field in the x,y,z -axis
    grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
    hr + // [1] heart rate in bpm
    hrRaw + // [16] undocumented data from Samsung's Hr raw sensor
    init_pres + // initial atmospheric pressure collected during calibration
    north_deg // body orientation in relation to magnetic north pole collected during calibration
```








