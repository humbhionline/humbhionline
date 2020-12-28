function blank(s){
    return !s || s.length === 0;
}

function logout(ev){
    Lockr.rm("SignUp");
    Lockr.rm("User");
    window.location.replace("/logout"); //Remove session cookie
}

function showError(err){
    if (err.response ){
        if (err.response.headers && err.response.headers.status === 401){
            logout();
        }else if (err.response.headers && err.response.headers.status === "413"){
             showErrorMessage("Size Uploaded Too Big");
        }else if (err.response.data && err.response.data.SWFHttpResponse.Error) {
            showErrorMessage(err.response.data.SWFHttpResponse.Error)
        }else {
            showErrorMessage(err.response.toString());
        }
    }else {
        showErrorMessage(err.toString());
    }
}

var errorTimeOut = undefined;
function showErrorMessage(msg,duration){
    let time = duration || 1500;
    $("#msg").removeClass("invisible");
    $("#msg").html(msg);
    if (errorTimeOut){
        clearTimeout(errorTimeOut);
        errorTimeOut = undefined;
    }
    return new Promise(function(resolve,reject){
        errorTimeOut = setTimeout(function(){
            $("#msg").addClass("invisible");
            resolve();
        },time);
    });

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

function showSpinner(){
    let a = typeof Android == "undefined" ? undefined : Android ;
    a && a.showSpinner();
}
function hideSpinner(){
    let a = typeof Android == "undefined" ? undefined : Android ;
    a && a.hideSpinner();
}
