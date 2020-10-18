{
    "title": "Order# ${Order.Id} just arrived." 
    "body": "An order just arrived for you to fulfill.",
    "data": { "click_url" : "https://${env.swf.host}/login?redirect_to=/orders?type=sales#Order-${Order.Id}"  } ,
    "icon": "https://${env.swf.host}/favicon.ico",
    "requireInteraction": true
}
