/*
* 路由配置
*
* 需要鉴权的路由：!meta.noAuth
* 左侧菜单显示：name && !hidden && meta.title
* 左侧菜单排序：能显示 && sort，升序排列
* 左侧菜单不折叠只有一个children的路由：alwaysShow
* 面包屑显示：meta.title
* 搜索选项显示：name && meta.title
* tab栏显示：name && meta.title
* tab栏固定显示：meta.affix
* 页面不缓存：!name && !meta.isDetailPage || meta.noCache
* 打开iframe：meta.iframe，不会重复打开相同src的iframe
* */
import Vue from 'vue'
import Router from 'vue-router'
import store from "@/store"
import NProgress from 'nprogress'
import {isUserExist} from "@/utils/storage"
import {auth, needAuth} from "@/utils/auth"
import {getPageTitle, transformWhiteList, metaExtend} from './util'
import {contextPath, routerMode} from '@/config'
import constantRoutes from '@/router/constant'
import authorityRoutes from '@/router/authority'

Vue.use(Router)

NProgress.configure({showSpinner: false})

const endRoute = [{path: '*', redirect: '/404', hidden: true}]

const whiteList = transformWhiteList(['/login', '/register', '/404', '/403'])

metaExtend(constantRoutes)
metaExtend(authorityRoutes)

const router = new Router({
    base: contextPath,
    mode: routerMode,
    scrollBehavior: () => ({y: 0}),
    routes: constantRoutes.concat(authorityRoutes, endRoute)
})

router.beforeEach(async (to, from, next) => {
    //使用redirect进行跳转时不显示进度条
    !to.path.startsWith('/redirect') && NProgress.start()

    //若是详情页之间的跳转，借助redirect避免组件复用
    const isToDetailPage = to.meta && to.meta.isDetailPage,
        isFromDetailPage = from.meta && from.meta.isDetailPage
    if (isToDetailPage !== undefined && isToDetailPage === isFromDetailPage) {
        //这里vue-router会报redirect错误
        return next({path: `/redirect${to.path}`, query: to.query})
    }

    document.title = getPageTitle(to)

    //白名单内不需要进行权限控制
    if (whiteList.some(reg => reg.test(to.path))) return next()

    const isLogin = isUserExist()

    //未登录时返回登录页
    if (!isLogin) return next({path: '/login', query: {redirect: to.fullPath}})

    //初始化菜单
    await initMenu()

    //已登录时访问登录页则跳转至首页
    if (to.path === '/login') return next({path: '/'})

    //页面不需要鉴权或有访问权限时通过
    if (!needAuth(to) || auth(to.path)) {
        iframeControl(to, from)
        return next()
    }

    //用户无权限访问时的动作
    next({path: '/403'})
})

router.afterEach(() => NProgress.done())

//初始化菜单和权限
function initMenu() {
    if (!store.state.resource.hasInitRoutes) {
        return store.dispatch('resource/init', store.state.user)
    }
    return Promise.resolve()
}

//判断是否需要打开iframe
function iframeControl(to, from) {
    const isRefresh = to.path === '/redirect' + from.path
    let iframe = to.meta && to.meta.iframe
    const operate = iframe ? 'open' : 'close'

    if (isRefresh) iframe = from.meta && from.meta.iframe

    return store.dispatch(`iframe/${operate}`, iframe)
}

export default router
