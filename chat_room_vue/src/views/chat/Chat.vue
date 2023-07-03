<template>
    <ChatBackground></ChatBackground>
    <main class="pc-box">
        <div class="box">
            <div class="m-box">
                <div class="chatContent">
                    <ChatContent></ChatContent>
                </div>
            </div>
        </div>
    </main>

    <div class="phone-box">
        <div class="phone-m-box">
            <div class="chatContent">
                <ChatContent ></ChatContent>
            </div>
        </div>
    </div>
</template>

<script setup>
import ChatBackground from '../../components/Chat/chatBackground.vue'
import ChatContent from '../../components/Chat/chatContent.vue'
import useUserStore from '../../store/modules/user'
import useSwitchStore from '../../store/modules/switch'
import Cookies from 'js-cookie'
import { watch, ref, onBeforeMount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import moadl from '../../plugins/modal'

const userStore = useUserStore()
const switchStore = useSwitchStore()

let userJsonStr = sessionStorage.getItem('userInfo');
let info = JSON.parse(userJsonStr);
const router = useRouter()

watch(async (v) => {
    if (sessionStorage.getItem('userInfo') == null) {
        moadl.msgError("请登录！")
        router.push({
            path: "/login"
        })
        setTimeout(() => {
            location.reload()
        }, 500);
        return
    }
    await userStore.getUserInfo(info.chatUserName);
});
userStore.userinfo = info



</script>


<style scoped>
.pc-box{
}

main {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 98vh;
    background-image: linear-gradient(to top, #fff1eb 0%, #ace0f9 100%);
}

.box {
    width: 1100px;
    height: 800px;
    background-color: #f5f5f5;
    border-radius: 10px;
}

.m-box {
    display: flex;
    width: 100%;
    height: 100%;
}

.chatContent {
    width: 100%;
    height: 100%;
    /* background-color: red; */
    /* border-left: rgba(121, 163, 159, 0.3) 1px solid; */
    overflow: hidden;
}

/* 手机端 */
.phone-box {
    width: 100%;
    height: 100%;
    display: none;
}

.phone-bottom-pos-box {
    width: 100%;
    height: 80px;
    position: fixed;
    bottom: 0;
    background: #8a98c9;
    display: flex;
    justify-content: space-around;
    align-items: center;
    overflow: hidden;
}

.phone-m-box {
    padding-bottom: 80px;
}

.phone-bottom-box {
    /* width: 100%; */
    height: 80%;
    cursor: pointer;
    display: flex;
    justify-content: center;
    align-content: center;
    flex-direction: column;
}

.phone-bottom-img-box {}

.phone-bottom-img-box img {
    height: 40px;
}

.phone-bottom-text {
    text-align: center;
}

@media (max-width: 1100px) {
    .chatContent {
        width: 100% !important;
    }

    .pc-box {
        display: none !important;
    }

    .phone-box {
        display: block !important;
    }
}
</style>