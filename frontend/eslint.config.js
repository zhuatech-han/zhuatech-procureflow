// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import vue from 'eslint-plugin-vue';
import globals from 'globals';
export default [...vue.configs['flat/recommended'],{files:['**/*.{js,vue}'],languageOptions:{globals:{...globals.browser,...globals.node}},rules:{'vue/multi-word-component-names':'off','vue/max-attributes-per-line':'off','vue/html-self-closing':'off','vue/singleline-html-element-content-newline':'off','vue/html-indent':'off','vue/html-closing-bracket-newline':'off','vue/html-closing-bracket-spacing':'off','vue/first-attribute-linebreak':'off','vue/attributes-order':'off','vue/html-quotes':'off','vue/multiline-html-element-content-newline':'off','no-unused-vars':'error'}}];
