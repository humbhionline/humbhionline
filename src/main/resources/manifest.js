var staticCacheName = 'mandi-v1';
var cacheRefreshTime = 0;
async function refreshCache(){
    var now = new Date().getTime();
    if (cacheRefreshTime < now - 60000){ //Older than 10 seconds
        var r = await fetch("/cache_versions/last",{headers:{'content-type' :'application/json'}});
        var response = await r.json();
        staticCacheName = 'mandi-v'+response.CacheVersion.VersionNumber;
        caches.keys().then(function(cacheNames){
            cacheNames.forEach(function(name,i){
                if (name !== staticCacheName){
                    caches.delete(name);
                }
            });
        })
        cacheRefreshTime = now;
    }
}

self.addEventListener('fetch', function(event){
    refreshCache();
    if (event.request.cache === 'only-if-cached' && event.request.mode !== 'same-origin') {
        return;
    }
    event.respondWith(
        caches.match(event.request).then(function (response) {
            let cacheable = event.request.method.toUpperCase() === 'GET' && event.request.url.startsWith("http") &&
                (  /^(.*)\.(jpg|jpeg|png|gif|ico|ttf|eot|svg|woff|woff2|css|js)/.test(event.request.url) )
            if (response !== undefined){
                console.log("Found in cache" + event.request.url );
                return response;
            }else if (cacheable){
                return fetch(event.request).then(function(response){
                    let responseClone = response.clone();
                    caches.open(staticCacheName).then(function (cache) {
                      cache.put(event.request, responseClone);
                    });
                    return response;
                });
            }else {
                return fetch(event.request);
            }
        })
    );

});

self.addEventListener('activate', function(event){
    //event.waitUntil(caches.delete(staticCacheName));
    event.waitUntil(clients.claim());
});



self.addEventListener('install', event => {
    console.log('Attempting to install service worker and cache static assets');
});

var deferredPrompt;
self.addEventListener('beforeinstallprompt', (e) => {
  // Prevent Chrome 67 and earlier from automatically showing the prompt
  e.preventDefault();
  // Stash the event so it can be triggered later.
  deferredPrompt = e;
  // Update UI notify the user they can add to home screen
  btnAdd.style.display = 'block';
});



self.addEventListener('push', function (event) {
  console.log('[Service Worker] Push Received.');
  console.log(`[Service Worker] Push had this data: "${event.data.text()}"`);
  var payload = event.data.json().notification;
  event.waitUntil(self.registration.showNotification(payload.title,
        Object.assign(payload,{requireInteraction : true})));
});

self.addEventListener('notificationclick', function (event) {
  console.log('[Service Worker] Notification click Received.');
  var payload = event.notification;
  event.notification.close();
  event.waitUntil(clients.openWindow(payload.data.click_url));
});
