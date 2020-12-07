function blank(s){
    return !s || s.length === 0;
}

function logout(ev){
    Lockr.rm("SignUp");
    Lockr.rm("User");
    return true;
}

function sendSubscriptionToServer(subscription){
    let device = Lockr.get("device");
    let user = Lockr.get("User");
    if (subscription &&  user && user.Id) {
        api().url("/devices/save").parameters({ 'Device': { 'DeviceId': subscription, 'UserId': user.Id } }).post()
        .then(function (response) {
            if (response.Devices && response.Devices.length > 0){
                Lockr.set("device",response.Devices[0]);
                if (device && device.Id && device.Id * 1.0 > 0 && device.Id !== Lockr.get("device").Id) {
                    api().url("/devices/destroy/"+device.Id).get().then(function(response) {
                        console.log("old subscription removed");
                    }).catch(function (err){
                        console.log("old token not deleted on the server");
                    });
                }
                console.log("Subscription made on the server");
            }
        });
    }else {
        console.log("Subscription not set on server");
    }
}
