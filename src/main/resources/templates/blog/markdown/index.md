<link rel="preload" as="style" href="/dashboard/css/style.css" onload="this.onload=null;this.rel='stylesheet'" >
<script type="text/javascript" src="/resources/scripts/node_modules/axios/dist/axios.min.js" defer ></script>
<script type="text/javascript" src="/resources/scripts/node_modules/vue/dist/vue.js" defer></script>
<script type="text/javascript">
    var vue;
    $(function(){
        Vue.component('htc',{
            props:["htc"],
            template :'<div v-html="htc"></div>'
        });
        Vue.component('accordion', {
            template: '#accordion',
            props: ['items'],
            methods: {
                openItem: function(item){
                    item.isopen = !  item.isopen
                },                
                setClass: function(item){
                    if (item.isopen == true ) {
                    return 'open'
                    }
                    return 'close'
                },
                enter: function(el, done){   
                    Velocity(el, 'slideDown', {duration: 400,  
                                            easing: "easeInBack"},
                                            {complete: done})
                },
                leave: function(el, done){
                    Velocity(el, 'slideUp', {duration: 400,  
                                            easing: "easeInBack"},
                                            {complete: done})
                },
            }, 
        })
        vue = new Vue({
            el : "#root",
            data : {
                left:"",
                right:"",
                items: [{
                    id: 1,
                    title: 'Competition law',
                    content: 'Schärer Attorneys at Law advises and represents you on questions of unfair competition and the anti-trust law, for example, for company mergers, anti-trust investigations and for the drafting of distribution agreements.',
                    isopen: true
                }, {
                    id:2,
                    title: 'Constitutional, community and administrative law',
                    content: 'Civil law regulates privities of contract between private persons, communities of persons and corporations. On the other hand, constitutional, community and administrative law is concerned with the legal relationship between a private person and the community sector (federation, cantons, communities, associations of communities), or amongst communities. The specialists at Schärer Attorneys at Law act as advisers and consultants for private persons as well as communities, and represent them in the legal proceedings of objection and appeal.',
                    isopen: false
                },
                {
                    id:3,
                    title: 'Construction, planning and environmental law',
                    content: 'Our specialists in the fields of construction, planning and environmental law advise and represent builders, planners and architects, corporations, affected neighboring communities and associations of communities in:',
                    isopen: false
                }]
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

<header>
    <template id="accordion">
        <ul>
            <li v-for="item in items" @click="openItem(item)">
                <div class="arrow_box" :class="{'arrow_box--open' : item.isopen}"></div>
                    {{item.title}}
                <div v-show="item.isopen" class="item">
                    {{item.content}}
                </div>  
            </li>
        </ul>
    </template>
    <div class="container">
        <div class="row">
            <nav class="navbar navbar-expand-lg navbar-light w-100">
                <div class="container p-0">
                    <a class="navbar-brand" href="#">
                        <img src="/dashboard/images/MandiLogo.svg" class="logo-img" alt=""><span>HumBhi</span>Online
                    </a>
                    <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#menu"
                        aria-controls="menu" aria-expanded="false" aria-label="Toggle navigation">
                        <span class="navbar-toggler-icon"></span>
                    </button>
                    <div class="collapse navbar-collapse" id="menu">
                        <div class="d-lg-flex justify-content-lg-end w-100">
                            <ul class="navbar-nav mr-0 mb-2 mb-lg-0">
                                <li class="nav-item">
                                    <a class="nav-link active" aria-current="page" onclick="hideMenu()"
                                        href="/dashboard#">Home</a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link" onclick="hideMenu()" href="/dashboard#why">Why choose</a>
                                </li>
                                <!-- <li class="nav-item">
                                    <a class="nav-link" href="#" tabindex="-1" aria-disabled="true">Testiminial</a>
                                </li> -->
                                <li class="nav-item">
                                    <a class="nav-link" onclick="hideMenu()" href="/dashboard#pricing" tabindex="-1"
                                        aria-disabled="true">Pricing</a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link" onclick="hideMenu()" href="/screenshots"
                                        tabindex="-1" aria-disabled="true">How To</a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link" onclick="hideMenu()" href="/dashboard#about-us"
                                        tabindex="-1" aria-disabled="true">About Us</a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link" onclick="hideMenu()" href="/support" tabindex="-1"
                                        aria-disabled="true">Support</a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link" onclick="hideMenu()" href="/blog" tabindex="-1"
                                        aria-disabled="true">Blog</a>
                                </li>
                            </ul>
                            <a v-if="!isAndroidApp"
                                href="https://play.google.com/store/apps/details?id=in.humbhionline"
                                class="btn btn-primary rounded-pill d-none d-sm-block ml-2" type="submit">Download
                                Now</a>
                        </div>
                    </div>
                </div>
            </nav>
        </div>
    </div>
</header>
<div id="root">
    <div class="container"> 
        <div class="row">
            <htc v-bind:htc="left" class="col-3 blog-links d-none d-xl-block" id="left" ref="left"></htc>
            <div class="col-sm blog-description">
                <!-- <accordion :items="items"></accordion> -->
                <htc v-bind:htc="right" id="right" ref="left"></htc>
            </div>
        </div>
    </div>
</div>
<footer class="text-center">
    <a class="navbar-brand" href="#">
        <img src="/dashboard/images/MandiLogoWhite.svg" class="logo-img" alt=""><span>HumBhi</span>Online
    </a>
    <ul class="list-unstyled mb-0 p-0 footer-link">
        <li class="nav-item">
            <a class="nav-link active" aria-current="page" href="/">Home</a>
        </li>
        <li class="nav-item">
            <a class="nav-link" href="#why">Why choose</a>
        </li>
        <!-- <li class="nav-item">
            <a class="nav-link" href="#" tabindex="-1" aria-disabled="true">Testiminial</a>
        </li> -->
        <li class="nav-item">
            <a class="nav-link" href="#pricing" tabindex="-1" aria-disabled="true">Pricing</a>
        </li>
        <li class="nav-item">
            <a class="nav-link" target="_blank" href="/screenshots" tabindex="-1" aria-disabled="true">How To</a>
        </li>
        <li class="nav-item">
            <a class="nav-link" onclick="hideMenu()" href="#about-us" tabindex="-1"
                aria-disabled="true">About Us</a>
        </li>
        <li class="nav-item">
            <a class="nav-link" href="/support" tabindex="-1" aria-disabled="true">Support</a>
        </li>
    </ul>
    <p class="mb-0">&nbsp;<a href="/dashboard/terms_and_conditions">Conditions of Use</a> <a href="privacy">Privacy</a> &copy; HumBhiOnline 2020</p>
</footer>
