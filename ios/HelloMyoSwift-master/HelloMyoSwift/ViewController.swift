import UIKit

class ViewController: UIViewController {

  @IBOutlet weak var accelerationProgressBar: UIProgressView!
  @IBOutlet weak var helloLabel: UILabel!
  @IBOutlet weak var accelerationLabel: UILabel!
  @IBOutlet weak var armLabel: UILabel!
  @IBOutlet weak var gyroscopeLabel: UILabel!
  
  var currentPose: TLMPose!
  
  override func viewDidLoad() {
    super.viewDidLoad()
    let notifer = NSNotificationCenter.defaultCenter()

    // Data notifications are received through NSNotificationCenter.
    notifer.addObserver(self, selector: "didConnectDevice:", name: TLMHubDidConnectDeviceNotification, object: nil)
    notifer.addObserver(self, selector: "didDisconnectDevice:", name: TLMHubDidDisconnectDeviceNotification, object: nil)
    // Posted whenever the user does a Sync Gesture, and the Myo is calibrated
    notifer.addObserver(self, selector: "didRecognizeArm:", name: TLMMyoDidReceiveArmRecognizedEventNotification, object: nil)
    // Posted whenever Myo loses its calibration (when Myo is taken off, or moved enough on the user's arm)
    notifer.addObserver(self, selector: "didLoseArm:", name: TLMMyoDidReceiveArmLostEventNotification, object: nil)

    // Notifications for orientation event are posted at a rate of 50 Hz.
    notifer.addObserver(self, selector: "didRecieveOrientationEvent:", name: TLMMyoDidReceiveOrientationEventNotification, object: nil)
    // Notifications accelerometer event are posted at a rate of 50 Hz.
    notifer.addObserver(self, selector: "didRecieveAccelerationEvent:", name: TLMMyoDidReceiveAccelerometerEventNotification, object: nil)
    // Posted when one of the pre-configued geatures is recognized (e.g. Fist, Wave In, Wave Out, etc)
    notifer.addObserver(self, selector: "didChangePose:", name: TLMMyoDidReceivePoseChangedNotification, object: nil)
    notifer.addObserver(self, selector: "didRecieveGyroScopeEvent:", name: TLMMyoDidReceiveGyroscopeEventNotification, object: nil)
  }

  override func didReceiveMemoryWarning() {
    super.didReceiveMemoryWarning()
  }
  
  @IBAction func didTapSettings(sender: AnyObject) {
    // Settings view must be in a navigation controller when presented
    let controller = TLMSettingsViewController.settingsInNavigationController()
    presentViewController(controller, animated: true, completion: nil)
  }
  
  func didConnectDevice(notification: NSNotification) {
    helloLabel.center = self.view.center
    
    armLabel.text = "Perform the Sync Gesture"
    helloLabel.text = "Hello Myo"
    
    accelerationProgressBar.hidden = false
    accelerationLabel.hidden = false
  }

  func didDisconnectDevice(notification: NSNotification) {
    helloLabel.text = ""
    armLabel.text = ""
    accelerationProgressBar.hidden = true
    accelerationLabel.hidden = true
  }

  func didRecognizeArm(notification: NSNotification) {
    let eventData = notification.userInfo as! Dictionary<NSString, TLMArmRecognizedEvent>
    let armEvent = eventData[kTLMKeyArmRecognizedEvent]!
    
    var arm = armEvent.arm == .Right ? "Right" : "Left"
    var direction = armEvent.xDirection == .TowardWrist ? "Towards Wrist" : "Toward Elbow"
    armLabel.text = "Arm: \(arm) X-Direction: \(direction)"
    helloLabel.textColor = UIColor.blueColor()
    
    armEvent.myo!.vibrateWithLength(.Short)
  }

  func didLoseArm(notification: NSNotification) {
    armLabel.text = "Perform the Sync Gesture"
    helloLabel.text = "Hello Myo"
    helloLabel.textColor = UIColor.blackColor()
    
    let eventData = notification.userInfo as! Dictionary<NSString, TLMArmLostEvent>
    let armEvent = eventData[kTLMKeyArmLostEvent]!
    armEvent.myo!.vibrateWithLength(.Short)
  }

  func didRecieveOrientationEvent(notification: NSNotification) {
    let eventData = notification.userInfo as! Dictionary<NSString, TLMOrientationEvent>
    let orientationEvent = eventData[kTLMKeyOrientationEvent]!
    
    let angles = GLKitPolyfill.getOrientation(orientationEvent)
    let pitch = CGFloat(angles.pitch.radians)
    let yaw = CGFloat(angles.yaw.radians)
    let roll = CGFloat(angles.roll.radians)
    let rotationAndPerspectiveTransform:CATransform3D = CATransform3DConcat(CATransform3DConcat(CATransform3DRotate (CATransform3DIdentity, pitch, -1.0, 0.0, 0.0), CATransform3DRotate(CATransform3DIdentity, yaw, 0.0, 1.0, 0.0)), CATransform3DRotate(CATransform3DIdentity, roll, 0.0, 0.0, -1.0))
    
    // Apply the rotation and perspective transform to helloLabel.
    helloLabel.layer.transform = rotationAndPerspectiveTransform
  }

  func didRecieveAccelerationEvent(notification: NSNotification) {
    let eventData = notification.userInfo as! Dictionary<NSString, TLMAccelerometerEvent>
    let accelerometerEvent = eventData[kTLMKeyAccelerometerEvent]!

    let acceleration = GLKitPolyfill.getAcceleration(accelerometerEvent);
    accelerationProgressBar.progress = acceleration.magnitude / 4.0;

    // Uncomment to show direction of acceleration
    //    let x = acceleration.x
    //    let y = acceleration.y
    //    let z = acceleration.z
    //    accelerationLabel.text = "Acceleration (\(x), \(y), \(z))"
  }

  func didChangePose(notification: NSNotification) {
    let eventData = notification.userInfo as! Dictionary<NSString, TLMPose>
    currentPose = eventData[kTLMKeyPose]!
    
    switch (currentPose.type) {
    case .Fist:
      helloLabel.text = "Fist"
      helloLabel.font = UIFont(name: "Noteworthy", size: 50)
      helloLabel.textColor = UIColor.greenColor()
    case .WaveIn:
      helloLabel.text = "Wave In"
      helloLabel.font = UIFont(name: "Courier New", size: 50)
      helloLabel.textColor = UIColor.greenColor()
    case .WaveOut:
      helloLabel.text = "Wave Out";
      helloLabel.font = UIFont(name: "Snell Roundhand", size: 50)
      helloLabel.textColor = UIColor.greenColor()
    case .FingersSpread:
      helloLabel.text = "Fingers Spread";
      helloLabel.font = UIFont(name: "Chalkduster", size: 50)
      helloLabel.textColor = UIColor.greenColor()
    case .ThumbToPinky:
      self.helloLabel.text = "Thumb to Pinky";
      self.helloLabel.font = UIFont(name: "Georgia", size: 50)
      self.helloLabel.textColor = UIColor.greenColor()
    default: // .Rest or .Unknown
      helloLabel.text = "Hello Myo"
      helloLabel.font = UIFont(name: "Helvetica Neue", size: 50)
      helloLabel.textColor = UIColor.blackColor()
    }
  }

  func didRecieveGyroScopeEvent(notification: NSNotification) {
    let eventData = notification.userInfo as! Dictionary<NSString, TLMGyroscopeEvent>
    let gyroEvent = eventData[kTLMKeyGyroscopeEvent]!

    let gyroData = GLKitPolyfill.getGyro(gyroEvent)
    // Uncomment to display the gyro values
    //    let x = gyroData.x
    //    let y = gyroData.y
    //    let z = gyroData.z
    //    gyroscopeLabel.text = "Gyro: (\(x), \(y), \(z))"
  }
}

