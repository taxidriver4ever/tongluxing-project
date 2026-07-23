import { createApp } from 'vue'
import App from './App.vue'
import router from './router/index.js'
import './styles/admin.css'
import './styles/form-polish.css'

createApp(App).use(router).mount('#app')
