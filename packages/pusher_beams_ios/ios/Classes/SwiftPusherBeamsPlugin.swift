import Flutter
import UIKit
import PushNotifications

public class SwiftPusherBeamsPlugin: FlutterPluginAppLifeCycleDelegate, FlutterPlugin, PusherBeamsApi, InterestsChangedDelegate {
    
    static var callbackHandler : CallbackHandlerApi? = nil
    
    var interestsDidChangeCallback : String? = nil
    var messageDidReceiveInTheForegroundCallback : String? = nil
    var onMessageOpenedAppCallback : String? = nil
    
    var beamsClient : PushNotifications?
    var started : Bool = false
    var deviceToken : Data? = nil
    var data: [String: NSObject]?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let messenger : FlutterBinaryMessenger = registrar.messenger()
        let instance : SwiftPusherBeamsPlugin = SwiftPusherBeamsPlugin()
      
        callbackHandler = CallbackHandlerApi(binaryMessenger: messenger)
        PusherBeamsApiSetup(messenger, instance)
        
        UNUserNotificationCenter.current().delegate = instance
        registrar.addApplicationDelegate(instance)
    }
    
    override public func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        if(started) {
            beamsClient?.registerDeviceToken(deviceToken)
            print("SwiftPusherBeamsPlugin: registerDeviceToken with token: \(String(describing: deviceToken))")

        } else {
            self.deviceToken = deviceToken
        }
    }

    @nonobjc public override func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        print("application.didReceiveRemoteNotification: \(userInfo)")
        let remoteNotificationType = self.beamsClient?.handleNotification(userInfo: userInfo)
        if remoteNotificationType == .ShouldIgnore {
          data = nil
          completionHandler(.noData)
          return
        }
        data = extractData(message: userInfo)
        completionHandler(.newData)
    }
    
    public func startInstanceId(_ instanceId: String, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        beamsClient = PushNotifications(instanceId: instanceId)
        beamsClient?.delegate = self
        beamsClient?.start()

        if(deviceToken != nil) {
            beamsClient?.registerDeviceToken(deviceToken!)
            print("SwiftPusherBeamsPlugin: registerDeviceToken with token: \(String(describing: deviceToken))")
            deviceToken = nil
        }
        started = true
    }

    public func addDeviceInterestInterest(_ interest: String, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        try? beamsClient!.addDeviceInterest(interest: interest)
    }

    public func removeDeviceInterestInterest(_ interest: String, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        try? beamsClient!.removeDeviceInterest(interest: interest)
    }
    
    public func getInitialMessage(completion: @escaping ([String : NSObject]?, FlutterError?) -> Void) {
        completion(data, nil)
    }

    public func getDeviceInterestsWithError(_ error: AutoreleasingUnsafeMutablePointer<FlutterError?>) -> [String]? {
        return beamsClient!.getDeviceInterests()
    }

    public func setDeviceInterestsInterests(_ interests: [String], error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        try? beamsClient!.setDeviceInterests(interests: interests)
    }

    public func clearDeviceInterestsWithError(_ error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        try? beamsClient!.clearDeviceInterests()
    }
    
    public func interestsSetOnDeviceDidChange(interests: [String]) {
        if (interestsDidChangeCallback != nil && (SwiftPusherBeamsPlugin.callbackHandler != nil)) {
            SwiftPusherBeamsPlugin.callbackHandler?.handleCallbackCallbackId(interestsDidChangeCallback!, callbackName: "onInterestChanges", args: [interests], completion: {_ in
                print("SwiftPusherBeamsPlugin: interests changed: \(interests)")
            })
        }
    }

    public func onInterestChangesCallbackId(_ callbackId: String, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        interestsDidChangeCallback = callbackId
    }

    public func setUserIdUserId(_ userId: String, provider: BeamsAuthProvider, callbackId: String, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        let tokenProvider = BeamsTokenProvider(authURL: provider.authUrl!) { () -> AuthData in
            let headers = provider.headers ?? [:]
            let queryParams: [String: String] = provider.queryParams ?? [:]
            return AuthData(headers: headers, queryParams: queryParams)
        }
        
        beamsClient!.setUserId(userId, tokenProvider: tokenProvider, completion: { error in
            guard error == nil else {
                SwiftPusherBeamsPlugin.callbackHandler?.handleCallbackCallbackId(callbackId, callbackName: "setUserId", args: [error.debugDescription], completion: {_ in
                  print("SwiftPusherBeamsPlugin: callback \(callbackId) handled with error")
                })
                return
            }

            SwiftPusherBeamsPlugin.callbackHandler?.handleCallbackCallbackId(callbackId, callbackName: "setUserId", args: [], completion: {_ in
                print("SwiftPusherBeamsPlugin: callback \(callbackId) handled")
            })
        })
    }

    public func clearAllStateWithError(_ error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        beamsClient!.clearAllState {
            print("SwiftPusherBeamsPlugin: state cleared")
        }
    }

    public func stopWithError(_ error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        beamsClient!.stop {
            print("SwiftPusherBeamsPlugin: stopped")
        }
        started = false
    }
    
    public func onMessageReceived(inTheForegroundCallbackId callbackId: String, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        messageDidReceiveInTheForegroundCallback = callbackId
    }
    
    public func onMessageOpenedApp(onMessageOpenedAppCallbackId callbackId: String, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        onMessageOpenedAppCallback = callbackId
    }
    
    public override func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        if (messageDidReceiveInTheForegroundCallback != nil && SwiftPusherBeamsPlugin.callbackHandler != nil) {
            let pusherMessage: [String : Any] = [
                "title": notification.request.content.title,
                "body": notification.request.content.body,
                "data": notification.request.content.userInfo["data"]
            ]
            
            SwiftPusherBeamsPlugin.callbackHandler?.handleCallbackCallbackId(messageDidReceiveInTheForegroundCallback!, callbackName: "onMessageReceivedInTheForeground", args: [pusherMessage], completion: {_ in
                print("SwiftPusherBeamsPlugin: message received: \(pusherMessage)")
            })
        }
    }

    public override func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        if (onMessageOpenedAppCallback != nil && SwiftPusherBeamsPlugin.callbackHandler != nil && self.data == nil) {
            let info = extractData(message: response.notification.request.content.userInfo)

            SwiftPusherBeamsPlugin.callbackHandler?.handleCallbackCallbackId(onMessageOpenedAppCallback!, callbackName: "onMessageOpenedApp", args: [info], completion: {_ in
                print("SwiftPusherBeamsPlugin: opened app with data: \(String(describing: info))")
            })
        }
        
        completionHandler()
    }
    
    private func extractData(message: [AnyHashable : Any]) -> [String: NSObject]? {
        let extraData = message["data"] as? [String: Any]
        return extraData?["info"] as? [String: NSObject]
    }
    
}
