> [中文文档](https://gold.xitu.io/post/583a9cfac59e0d006b3b597d)

# RxBle

#### Program Android BLE function with RxJava 
## SETUP
Make sure your project has been introduced RxJava in `build.gradle`,like this:
```gradle
compile 'io.reactivex:rxjava:1.2.3'
compile 'io.reactivex:rxandroid:1.2.1'
```
Then you can copy `RxBle.java` to your project.
## USEAGE
1.Get the singleton of the RxBle
```java
RxBle rxBle = RxBle.getInstance()
```
or if you know the target BLE device name such as `Test`
```java
RxBle rxBle = RxBle.getInstance().setTargetDevice("Test")
```
2.Open bluetooth by no asking
```java
mRxBle.openBle(this);
```
3.If you did not use `setTargetDevice` to set target device name,you should **set a listener** in RxBle to get a list of devices when scanning,then connect the target device by yourself
```java
rxBle.setScanListener(new RxBle.BleScanListener() {
	@Override
	public void onBleScan(BluetoothDevice bleDevice, int rssi, byte[] scanRecord) {
		// Get list of devices and other information
		if(bledevice.getName().equals("Test")){
			rxBle.connectDevice(bleDevice);
		}
	}
});
```
4.Communication between Android phone and BLE device
```java
rxBle.sendData(data);
// or send with delay
rxBle.sendData(data,delay);

rxBle.receiveData().subscribe(new Action1<String>() {
	@Override
	public void call(String receiveData) {
		// Data will be received while they sent by BLE device
	}
});
```
5.Close BLE
```java
rxBle.closeBle();
```
## ADDITIONAL
The best practice is fork and edit it,adapt to your project needs.
## FAQ
- Twitter:[@qiantao94](https://twitter.com/qiantao94)
- Gmail:[qiantao94@gmail.com](qiantao94@gmail.com)
