import { createPinia } from 'pinia'
import { createApp } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTree,
  ElLoading,
} from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import { router } from './router'
import './styles.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

;[
  ElAlert,
  ElButton,
  ElCard,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTree,
].forEach((component) => app.component(component.name!, component))

app.use(ElLoading)
app.mount('#app')
