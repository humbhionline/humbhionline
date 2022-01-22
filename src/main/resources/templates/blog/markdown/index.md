<link rel="preload" as="style" href="../css/style.css" onload="this.onload=null;this.rel='stylesheet'" >
<script type="text/javascript" src="/resources/scripts/node_modules/axios/dist/axios.min.js" defer ></script>
<script type="text/javascript" src="/resources/scripts/node_modules/vue/dist/vue.js" defer></script>
<script type="text/javascript" >
    function loadRight(ev,link){
        ev && ev.preventDefault();
        $("#right").load("/blog/markdownFragment/"+link, {} , function(){});
    }
    $(
        function () {
            new Vue({
                el: "#root",
                data: {
                    root : {
                        tocFragment : "" , 
                    } , 
                },
                created: function () {
                    let self = this;
                    api().url("/blog/markdownFragment/toc").get().then(function(r){
                        self.root.tocFragment = r;  
                    }).then(function(){
                        loadRight(null,"/blog/markdownFragment/whyonline");
                    });
                },
            });
        }
   );
</script>


<div id="root" style="w-100">
    <div class="container" > 
        <div class="row">
            <div class="col-4" id="toc" v-html="root.tocFragment">
            </div>
            <div class="col-8" id="right" >
            </div>
        </div>
    </div>
</div>
