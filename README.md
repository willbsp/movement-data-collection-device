# Low-cost Wearable Movement Data Collection Device

This project consists of code for the firmware of a low-cost wearable movement device and the associated Android app used to configure it. This project was created for a final year Computer Science dissertation project. The write-up for this project is available upon request.

## Motivation
The motivation for this project was to create a low-cost wearable movement data (accelerometer and gyroscope data) for use in movement disorder research. This project consists of a cost-effective wearable device for collecting movement data, where the important considerations include cost and the accuracy of the data, such that it can be used as input for a machine learning model. The device is configured wirelessly using an Android companion application. Here, the user is able to configure device settings such as sample rate, tracking time, and type of data collected. Additionally, the app provides the ability to inspect data collected by the device and annotate the data with predicted activities. By demonstrating that data collected from the device can be used with a basic activity prediction model, this shows that the device can be applied to further research into its efficacy in movement disorder research.

## Screenshots

![Configuring the device](screenshots/screenshot-1.gif)
![Making predictions](screenshots/screenshot-2.gif)

## Licence

```
Copyright (C) 2024 Will Spooner

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
