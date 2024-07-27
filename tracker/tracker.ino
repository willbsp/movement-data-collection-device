#include <ArduinoBLE.h>
#include <Arduino_LSM6DS3.h>
#include <WiFiNINA.h>
#include <SPI.h>
#include <SD.h>

// BLE defines
#define BLE_UUID_CONFIGURATION_SERVICE "B521C54C-3468-409D-AA2C-643E3D04EE10"
#define BLE_UUID_TRACKING "39F8907B-F89C-4AAF-AD5D-B4E6B68C5594"
#define BLE_UUID_HIGH_SAMPLE_RATE "B9ECC76A-B4BC-4E81-B2E1-1899A574D620"
#define BLE_UUID_DATA_TO_RECORD "DF705F32-0774-4CBF-B9CB-5AD7B792926A"
#define BLE_UUID_DATA_FILE_NAME "B4BB6F59-0C2C-4074-AF0C-BADFA440BC2F"
#define BLE_UUID_READ_MILLIS "4DEC5AB8-78B5-46F6-A3B0-2303D56FAC60"
#define BLE_DEVICE_NAME "tracker"
#define BLE_UPDATE_INTERVAL (50)

// SD card configuration
#define SD_SETTING_HIGH_SAMPLE_RATE "high_sample_rate"
#define SD_SETTING_DATA_TO_RECORD "data_to_record"
#define SD_SETTING_DATA_FILE_NAME "file_name"
#define SD_SETTING_READ_MILLIS "read_millis"
#define SD_SETTING_AUTO_START "auto_start"

#define SD_SETTING_HIGH_SAMPLE_RATE_DEFAULT (false)  // 104Hz
#define SD_SETTING_DATA_TO_RECORD_DEFAULT (0)        // both
#define SD_SETTING_DATA_FILE_NAME_DEFAULT "default.csv"
#define SD_SETTING_READ_MILLIS_DEFAULT (5000)
#define SD_SETTING_AUTO_START_DEFAULT (false)

#define SD_CONFIG_FILE_NAME "config.txt"
#define SD_CHIP_SELECT_PIN (4)

// BLE service and characteristic setup
BLEService configurationService(BLE_UUID_CONFIGURATION_SERVICE);
BLEBoolCharacteristic trackingCharacteristic(BLE_UUID_TRACKING, BLERead | BLEWrite | BLENotify);
BLEBoolCharacteristic highSampleRateCharacteristic(BLE_UUID_HIGH_SAMPLE_RATE, BLERead | BLEWrite);
BLEStringCharacteristic dataFileNameCharacteristic(BLE_UUID_DATA_FILE_NAME, BLERead | BLEWrite, 32);
BLEIntCharacteristic readMillisCharacteristic(BLE_UUID_READ_MILLIS, BLERead | BLEWrite);
BLEIntCharacteristic dataToRecordCharacteristic(BLE_UUID_DATA_TO_RECORD, BLERead | BLEWrite);

// In-memory configuration settings
bool highSampleRate = SD_SETTING_HIGH_SAMPLE_RATE_DEFAULT;
int dataToRecord = SD_SETTING_DATA_TO_RECORD_DEFAULT;  // 0 = both, 1 = accel only, 2 = gyro only
String dataFileName = SD_SETTING_DATA_FILE_NAME_DEFAULT;
int readMillis = SD_SETTING_READ_MILLIS_DEFAULT;
bool autoStart = SD_SETTING_AUTO_START_DEFAULT;

// Tracking variables
bool tracking = false;
long trackingStartMillis = 0;  // millis() when tracking is started
File dataFile;                 // Data file to record to

// ----------------------
// Arduino core functions
// ----------------------

void setup() {

  Serial.begin(9600);

  // DEBUG wait for serial connection
  //while (!Serial) { ; }

  // setup modules
  setupStorage();

  // read configuration values
  readConfiguration();

  // setup BLE service
  bleConfiguratorSetup();

  if (autoStart) {
    startTracking();
  }
}

void loop() {
  bleConfiguratorTask();
  if (tracking) {
    imuTrackingTask();
  }
}

// -----
// Setup
// -----

// Setup the SD card
void setupStorage() {
  if (!SD.begin(SD_CHIP_SELECT_PIN)) {
    Serial.println("ERROR: Failed to initialise SD module!");
    Serial.println("ERROR: Please check SD card connection, program stalled!");
    while (1) { ; };
  }
  Serial.println("SD: Initialised successfully");
  if (!SD.exists(SD_CONFIG_FILE_NAME)) {  // create settings file if it does not exist
    rebuildSettingsFile();
  }
}

// Setup the IMU
void setupSensors() {

  // Initialise IMU
  // IMU library was modified to pass though the highSampleRate flag
  // which will set the sensor sample rate to 208Hz
  if (!IMU.begin(highSampleRate)) {
    Serial.println("ERROR: Failed to initialise IMU, program stalled!");
    while (1);
  }
  Serial.println("IMU: Initialised successfully");

  // Print sample rates
  Serial.print("IMU: sample rate = ");
  if (highSampleRate) {
    Serial.println("208Hz");
  } else {
    Serial.println("104Hz");
  }
}

// IMU .end() must be called so that sample rate can be reconfigued between sessions
void endSensors() {
  IMU.end();
}

// ---------------------
// SD card configuration
// ---------------------

// Read the configuration from the SD card into memory
void readConfiguration() {
  File configFile = SD.open(SD_CONFIG_FILE_NAME);
  if (configFile) {
    highSampleRate = readConfigurationSetting(configFile, SD_SETTING_HIGH_SAMPLE_RATE).toInt();
    dataToRecord = readConfigurationSetting(configFile, SD_SETTING_DATA_TO_RECORD).toInt();
    dataFileName = readConfigurationSetting(configFile, SD_SETTING_DATA_FILE_NAME);
    readMillis = readConfigurationSetting(configFile, SD_SETTING_READ_MILLIS).toInt();
    autoStart = readConfigurationSetting(configFile, SD_SETTING_AUTO_START).toInt();
    configFile.close();
  } else {
    Serial.print("ERROR: Could not open config file ");
    Serial.println(SD_CONFIG_FILE_NAME);
  }
}

// Return the value of a single configuration item
String readConfigurationSetting(File config, String key) {
  while (config.available()) {
    String setting = config.readStringUntil('=');
    if (setting == key) {
      String value = config.readStringUntil('\n');
      Serial.print("CONFIGURATION: ");
      Serial.print(key);
      Serial.print(" = ");
      Serial.println(value);
      return value;
    }
  }
  Serial.print("CONFIGURATION: Failed to get value for key ");
  Serial.println(key);
  return "";
}

// Will write configuration setting at where wherever the cursor currently is
void writeConfigurationSetting(File config, String key, String value) {
  Serial.print("CONFIGURATION: Writing configuration setting - ");
  Serial.println(key);
  config.write(key.c_str());
  config.write('=');
  config.write(value.c_str());
  config.write('\n');
}

// Deletes old configuration, replacing it with an up-to-date version from the in-memory configuration
void rebuildSettingsFile() {
  Serial.println("CONFIGURATION: Rebuilding settings file...");

  SD.remove(SD_CONFIG_FILE_NAME);
  File configFile = SD.open(SD_CONFIG_FILE_NAME, FILE_WRITE);

  writeConfigurationSetting(configFile, SD_SETTING_HIGH_SAMPLE_RATE, String(highSampleRate));
  writeConfigurationSetting(configFile, SD_SETTING_DATA_TO_RECORD, String(dataToRecord));
  writeConfigurationSetting(configFile, SD_SETTING_DATA_FILE_NAME, dataFileName);
  writeConfigurationSetting(configFile, SD_SETTING_READ_MILLIS, String(readMillis));
  writeConfigurationSetting(configFile, SD_SETTING_AUTO_START, String(autoStart));

  configFile.flush();
  configFile.close();
  Serial.println("CONFIGURATION: Settings file rebuilt!");
}


// -----------------
// BLE configuration
// -----------------

// Setup the BLE configurator, returns true if successful
bool bleConfiguratorSetup() {

  // Initialise BLE
  if (!BLE.begin()) {
    Serial.println("ERROR: Failed to start BLE!");
    return false;
  }
  Serial.print("BLE: Address - ");
  Serial.println(BLE.address());
  BLE.setLocalName(BLE_DEVICE_NAME);
  BLE.setAdvertisedService(configurationService);

  // Setup characteristic event handlers
  trackingCharacteristic.setEventHandler(BLEWritten, trackingCharacteristicWritten);
  highSampleRateCharacteristic.setEventHandler(BLEWritten, highSampleRateCharacteristicWritten);
  dataToRecordCharacteristic.setEventHandler(BLEWritten, dataToRecordCharacteristicWritten);
  dataFileNameCharacteristic.setEventHandler(BLEWritten, dataFileNameCharacteristicWritten);
  readMillisCharacteristic.setEventHandler(BLEWritten, readMillisCharacteristicWritten);

  // Add characteristics to the service
  configurationService.addCharacteristic(trackingCharacteristic);
  configurationService.addCharacteristic(highSampleRateCharacteristic);
  configurationService.addCharacteristic(dataToRecordCharacteristic);
  configurationService.addCharacteristic(dataFileNameCharacteristic);
  configurationService.addCharacteristic(readMillisCharacteristic);

  // Add service to BLE
  BLE.addService(configurationService);

  // Write default values from current in-memory values (may have been previously read from SD card)
  trackingCharacteristic.writeValue(false);
  highSampleRateCharacteristic.writeValue(highSampleRate);
  dataToRecordCharacteristic.writeValue(dataToRecord);
  dataFileNameCharacteristic.writeValue(dataFileName.c_str());
  readMillisCharacteristic.writeValue(readMillis);

  // Setup connection and disconnection event handlers, and advertise to nearby devices
  BLE.setEventHandler(BLEConnected, bleConnectionEventHandler);
  BLE.setEventHandler(BLEDisconnected, bleDisconnectEventHandler);
  BLE.advertise();

  return true;
}

// Polls BLE every BLE_UPDATE_INTERVAL to ensure event handlers will trigger
void bleConfiguratorTask() {
  static int previous = 0;
  int current = millis();
  if (current - previous >= BLE_UPDATE_INTERVAL) {
    previous = current;
    BLE.poll();
  }
}

// ------------------
// BLE event handlers
// ------------------

void bleConnectionEventHandler(BLEDevice central) {
  Serial.print("BLE: Connected to central: ");
  Serial.println(central.address());
}

// Will start advertising again on a BLE disconnect
void bleDisconnectEventHandler(BLEDevice central) {
  Serial.print("BLE: Disconnected from central: ");
  Serial.println(central.address());
  BLE.advertise();
}

void trackingCharacteristicWritten(BLEDevice central, BLECharacteristic characteristic) {
  Serial.println("BLE: Tracking characteristic written");
  bool value = trackingCharacteristic.value();
  if (tracking == false && value == true) {
    startTracking();
  } else if (tracking == true && value == false) {
    Serial.println("BLE: Tracking interrupted!");
    endTracking();
  }
}

void dataFileNameCharacteristicWritten(BLEDevice central, BLECharacteristic characteristic) {
  String value = dataFileNameCharacteristic.value();
  Serial.print("BLE: Data file name written! New value is: ");
  Serial.println(value);
  dataFileName = value;
  rebuildSettingsFile();
}

void readMillisCharacteristicWritten(BLEDevice central, BLECharacteristic characteristic) {
  int value = readMillisCharacteristic.value();
  Serial.print("BLE: Read millis written! New value is: ");
  Serial.println(value);
  readMillis = value;
  rebuildSettingsFile();
}

void highSampleRateCharacteristicWritten(BLEDevice central, BLECharacteristic characteristic) {
  bool value = highSampleRateCharacteristic.value();
  Serial.print("BLE: High sample rate written! New value is: ");
  Serial.println(value);
  highSampleRate = value;
  rebuildSettingsFile();
}

void dataToRecordCharacteristicWritten(BLEDevice central, BLECharacteristic characteristic) {
  int value = dataToRecordCharacteristic.value();
  Serial.print("BLE: Data to record written! New value is: ");
  Serial.println(value);
  dataToRecord = value;
  rebuildSettingsFile();
}

// --------
// Tracking
// --------

// Will initialise IMU, open a new data file, then start the tracking task
void startTracking() {
  Serial.println("TRACKING: Starting tracking....");
  setupSensors();
  if (SD.exists(dataFileName)) {  // If file with same name exists, remove
    SD.remove(dataFileName);
  }
  dataFile = SD.open(dataFileName, FILE_WRITE);
  if (dataFile) {
    switch (dataToRecord) {
      case 0:  // both
        dataFile.print("t,ax,ay,az,gx,gy,gz\n");
        break;
      case 1:  // accel only
        dataFile.print("t,ax,ay,az\n");
        break;
      case 2:  // gyro only
        dataFile.print("t,gx,gy,gz\n");
        break;
    }
    tracking = true;
    trackingStartMillis = millis();
  } else {
    Serial.print("TRACKING: Error opening data file ");
    Serial.println(dataFile);
  }
}

// Flush any final writes, close the data file, and finish tracking
void endTracking() {
  dataFile.flush();
  dataFile.close();
  endSensors();
  Serial.print("TRACKING: Done! Read for ");
  Serial.print(millis() - trackingStartMillis);
  Serial.println(" millis");
  tracking = false;                          // Will cause tracking task to exit
  trackingCharacteristic.writeValue(false);  // Update characteristic, this will notify the companion if connected
  trackingStartMillis = 0;                   // Reset the tracking start time
}

// Called in Arduino loop, will check to see if data is available, and if so, then record to data file
void imuTrackingTask() {
  if ((millis() - trackingStartMillis) < readMillis) {

    switch (dataToRecord) {
      case 0:                                                           // both
        if (IMU.accelerationAvailable() && IMU.gyroscopeAvailable()) {  // Availability is defined by sample rate
          float t, ax, ay, az, gx, gy, gz;                              // Temp values to store sensor reads
          t = millis();                                                 // Called again to ensure close to sensor reads
          IMU.readAcceleration(ax, ay, az);
          IMU.readGyroscope(gx, gy, gz);
          dataFile.print(t);
          dataFile.print(",");
          dataFile.print(ax);
          dataFile.print(",");
          dataFile.print(ay);
          dataFile.print(",");
          dataFile.print(az);
          dataFile.print(",");
          dataFile.print(gx);
          dataFile.print(",");
          dataFile.print(gy);
          dataFile.print(",");
          dataFile.print(gz);
          dataFile.print("\n");
        }
        break;
      case 1:  // accel only
        if (IMU.accelerationAvailable()) {
          float t, ax, ay, az;
          t = millis();
          IMU.readAcceleration(ax, ay, az);
          dataFile.print(t);
          dataFile.print(",");
          dataFile.print(ax);
          dataFile.print(",");
          dataFile.print(ay);
          dataFile.print(",");
          dataFile.print(az);
          dataFile.print("\n");
        }
        break;
      case 2:  // gyro only
        if (IMU.gyroscopeAvailable()) {
          float t, gx, gy, gz;
          t = millis();
          IMU.readGyroscope(gx, gy, gz);
          dataFile.print(t);
          dataFile.print(",");
          dataFile.print(gx);
          dataFile.print(",");
          dataFile.print(gy);
          dataFile.print(",");
          dataFile.print(gz);
          dataFile.print("\n");
        }
    }

  } else {
    // Exit when tracking time has elasped
    endTracking();
  }
}
