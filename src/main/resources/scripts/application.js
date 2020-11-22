const applicationServerPublicKey = "BEaDFsmqV0J9fYbv4tRI-p3qn-ckscovP2S5Q8dusiRCDzIPf4qalpwVW2PlAEyH_6YBc_k_Vj56-yQgtKhhbIY";

function urlB64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - base64String.length % 4) % 4);
  const base64 = (base64String + padding)
    .replace(/\-/g, '+')
    .replace(/_/g, '/');

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}


function sw_start(callback){
    if ('serviceWorker' in navigator && 'PushManager' in window) {
        navigator.serviceWorker.register('/manifest.js',{ updateViaCache : 'none'} ).then(function(registration) {
          callback && callback(registration);
          console.log('ServiceWorker registration successful with scope: ', registration.scope);
        }, function(err) {
          // registration failed :(
          console.log('ServiceWorker registration failed: ', err);
        });
    }else {
        console.warn('navigator' + navigator );
        console.warn('serviceWorker' + 'serviceWorker' in navigator);
        console.warn('PushManager' + 'PushManager' in window);
        console.warn('Push messaging is not supported');
    }
}

function registerServiceWorker(callback) {
    sw_start(callback);
}

function subscribe(updateSubscriptionOnServer) {
    
    if (typeof Android != "undefined" ){
        Android.startFirebaseService(updateSubscriptionOnServer.name);
    }else {
        registerServiceWorker((swReg) => {
            createSubscription(swReg, updateSubscriptionOnServer);
        });
    }
}

function unsubscribe(updateSubscriptionOnServer) {
    registerServiceWorker((swReg) => {
        removeSubscription(swReg, updateSubscriptionOnServer);
    });
}

function handlePermission(updateSubscriptionOnServer) {
    if (Notification.permission === 'denied') {
        // console.log('Push Messaging Blocked.');
        updateSubscriptionOnServer(null);
        return;
    }
}
function marshall(subscription) {
    var key = subscription.getKey ? subscription.getKey('p256dh') : '';
    var auth = subscription.getKey ? subscription.getKey('auth') : '';
    var subscriptionJSON = {
        keys: {
            p256dh: key ? btoa(String.fromCharCode.apply(null, new Uint8Array(key))) : '',
            auth: auth ? btoa(String.fromCharCode.apply(null, new Uint8Array(auth))) : '',
        },
        endpoint: subscription.endpoint,
    };
    return JSON.stringify(subscriptionJSON);
}

function createSubscription(swRegistration, updateSubscriptionOnServer) {
    var isSubscribed = false;
    swRegistration.pushManager.getSubscription()
        .then(function (subscription) {
            isSubscribed = !(subscription === null);
            if (!isSubscribed) {
                subscribeUser(swRegistration, updateSubscriptionOnServer);
            } else {
                updateSubscriptionOnServer(marshall(subscription));
            }
        }).catch(function (err) {
            subscribeUser(swRegistration,updateSubscriptionOnServer);
        })
}

function removeSubscription(swRegistration, updateSubscriptionOnServer) {
    const applicationServerKey = urlB64ToUint8Array(applicationServerPublicKey);
    swRegistration.pushManager.getSubscription()
        .then(function (subscription) {
            if (subscription) {
                return subscription.unsubscribe();
            }
        })
        .catch(function (error) {
            // console.log('Error unsubscribing', error);
        })
        .then(function () {
            updateSubscriptionOnServer(null);
        });
}

function subscribeUser(swRegistration, updateSubscriptionOnServer) {
    const applicationServerKey = urlB64ToUint8Array(applicationServerPublicKey);

    swRegistration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: applicationServerKey
    }).then(function (subscription) {
        // console.log('User is subscribed.');
        updateSubscriptionOnServer(marshall(subscription));
    }).catch(function (err) {
        // console.log('Failed to subscribe the user: ', err);
        handlePermission(updateSubscriptionOnServer);
    });
}

/*
$(function () {
    sw_start();
});
*/
