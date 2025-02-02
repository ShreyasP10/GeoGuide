GeoGuide
<br>
GeoGuide is a compass and location-tracking app designed to provide accurate direction and real-time location details using your phone’s magnetometer, accelerometer, and GPS sensors. <br>
The app ensures a smooth and stabilized compass experience by implementing a low-pass filter to reduce sensor noise and improve accuracy.
<br>

To use this app you need to configure your Google Map API here are steps 
<br>
<br>






<div>
1. Set up the Google Maps API in Google Cloud Console:<br>
First, visit the Google Cloud Console and either create a new project or select an existing one. Enable the "Google Maps Android API" in the API library. After enabling the API, generate an API key that will be used to authenticate your app when it makes requests to Google Maps services.
<br><br>
2. Add the API Key to Your Android App:<br>
Open your Android project and add the API key you obtained from the Google Cloud Console. This key needs to be added to the strings.xml file, which will be referenced later in the manifest for authentication.
<br><br>
3. Update the AndroidManifest.xml:<br>
Modify your AndroidManifest.xml file to include the required permissions for accessing the internet and location services. Additionally, you need to add metadata to the manifest that links to your Google Maps API key so that your app can use Google Maps.
<br><br>
4. Add the Google Maps SDK Dependency:<br>
In the build.gradle file of your app, you need to add the dependency for the Google Maps SDK to integrate the necessary libraries. Make sure to sync your project after adding this dependency so that all required components are correctly included.
</div>
