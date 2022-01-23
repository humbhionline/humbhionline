<link rel="preload" as="style" href="../css/style.css" onload="this.onload=null;this.rel='stylesheet'" >
<script type="text/javascript" src="/resources/scripts/node_modules/axios/dist/axios.min.js" defer ></script>
<script type="text/javascript" src="/resources/scripts/node_modules/vue/dist/vue.js" defer></script>
<script type="text/javascript" >
    var vue;
    $(function(){
        Vue.component('htc',{
            props:["htc"],
            template :'<div v-html="htc"></div>'
        });
        vue = new Vue({
            el : "#root",
            data : {
                left:"",
                right:"",
            },
            created: function(){
                this.load(null,"left","toc");
                this.load(null,"right","whyonline");
            },
            methods: {
                load: function(ev,ref,link){
                    let self = this;
                    ev && ev.preventDefault();
                    api().url("/blog/markdownFragment/"+link).get().then(function(r){
                        self[ref] = r;
                    });
                }
            }
        });
    });
</script>


<div id="root" style="w-100">
    <div class="container" > 
        <div class="row">
            <htc v-bind:htc="left" class="col-4" id="left" ref="left"></htc>
            <htc v-bind:htc="right" class="col-8" id="right" ref="left"></htc>
        </div>
    </div>
</div>
