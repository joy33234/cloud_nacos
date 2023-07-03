<template>
    <div class="container" style="width:100%;">

        <div  >

            <div class="chat_list" ref="isShow">
                 <div ref="isLoad" class="loader_box">
                    <Loader></Loader>
                 </div>

                <!-- 聊天记录 -->
                <div v-for="(item,index) in msglogList" :key="index"  :ref="'listItem-' + index" >
                    <div v-if="item.senderGuId != userStore.userinfo.userId" class="chat_other_box">
                        <div class="chat_img_box">
                            <img class="chat_img"  src="../../assets/img/chatgpt.png" alt="">
                        </div>
                        <div id="gptMessageContent" v-html="item.chatLogContent" class="message_box"  style = "white-space: break-spaces !important;"></div>
                    </div>
                    <div  v-if="item.senderGuId == userStore.userinfo.userId"  class="chat_self_box">
                        <div v-html="item.chatLogContent" class="message_self_box"></div>
                            <div class="chat_img_box">
                                <img class="chat_img" :src=userStore.userinfo.chatUserImg alt="">
                            </div>
                        </div>
                    </div>
                </div>

                <div>
                    <div class="toolbar" >
                        <div class="tools">
                            <el-upload class="avatar-uploader" :action="uploadImgUrl" :show-file-list="false" :headers="headers"
                                :on-success="handleImgSuccess" :data="{ chat_img: msg, }  ">
                                <img src="../../assets/img/pic.png" alt="">
                            </el-upload>
                        </div>
                    </div>
                    <div ref="imageShowRef"  class="send_box" maxlength="800"  contenteditable="true">
                    </div>
                    <div class="send_box">
                        <div ref="inputRef"  class="textarea" maxlength="800" autofocus
                            @keydown="handleEntry($event)" contenteditable="true">
                        </div>
                    <!-- <textarea  class="textarea" maxlength="800" autofocus disabled></textarea> -->
                    </div>
                <div class="btn_box">
                    <!-- <button  class="close" @click="handleColse">关闭(c)</button> -->
                    <button   @click="handelFix($event)">语法校验</button>
                    <button   @click="handelImages($event)">生成图片</button>
                    <button   @click="handelSend($event)">聊天发送</button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import ChatList from './chatList.vue'
import signalr from '../../utils/signalR'
import useUserStore from '../../store/modules/user'
import useSwitchStore from '../../store/modules/switch'
import useSocketCahtStore from '../../store/modules/socketchat'
import useChatStore from "../../store/modules/chat";
import modal from '../../plugins/modal';
import { read, slefNewMsg,chat,fix,images } from '../../api/chatLog/chatLog'
import { upload } from '../../api/common'
import V3Emoji from 'vue3-emoji'
import 'vue3-emoji/dist/style.css'
import { AddLocation } from '@element-plus/icons-vue'
import Loader from '../../components/Login/loader.vue'
import { getToken } from '../../utils/auth'


const baseUrl = "/stage-api"
const uploadImgUrl = ref(baseUrl + "/pandora/ai/images/upload") // 上传的图片服务器地址
const userStore = useUserStore()
const switchStore = useSwitchStore()
const SocketCahtStore = useSocketCahtStore()
const chatStore = useChatStore()
const isShow = ref();
const isLoad = ref();
let userInfo = userStore.userinfo
let connectionID = reactive()
let selfconnectionID = reactive()
const msg = ref();
const inputRef = ref();
const imageShowRef = ref();
const titleName = ref();
let isAdd = ref(false)
let selfMsgList = ref([])
let isMore = ref(false);
let msglogList = ref([])
let logQueryData = ref(null)
let tcWidth = ref()
const imagesRes = ref([])

const optionsName = {
    'Smileys & Emotion': '笑脸&表情',
    'Food & Drink': '食物&饮料',
    'Animals & Nature': '动物&自然',
    'Travel & Places': '旅行&地点',
    'People & Body': '人物&身体',
    Objects: '物品',
    Symbols: '符号',
    Flags: '旗帜',
    Activities: '活动'
};


const chatData = {
    "chatLogType": "1",
    "senderGuId": userStore.userinfo.userId,
    "receiverGuId": "-1",
    "chatLogContent": null,
    "chatLogSendTime": null,
    "images": [],
    "sender": userStore.userinfo.username
  }

const customSize = {
    //   'V3Emoji-width': '300px',
    'V3Emoji-height': '20rem',
    'V3Emoji-fontSize': '1rem',
    'V3Emoji-itemSize': '2rem'
};


const headers = {
    Authorization: 'Bearer ' + getToken()
};


async function handelSend(e) {

    if (inputRef.value.innerHTML == false || inputRef.value.innerHTML == null) {
        modal.msgError('请输入内容');
        inputRef.value.innerHTML = null
        return
    }

    SocketCahtStore.chatOnlineUsers.forEach(function (item, index) {
        if (item.userGuid == userInfo.userId) {
            selfconnectionID = item.connectionId
        }
        if (item.userGuid == chatStore.chatUser.friendGuId) {
            connectionID = item.connectionId
        }
    })
    // console.log(inputRef.value.innerHTML,"xianshang asd");
    //signalr.SR.invoke( selfconnectionID, connectionID, userInfo.userId,  inputRef.value.innerHTML).catch(function (err) {
        //console.error("err.toString()")
    //})

    chatData.chatLogType = 1
    chatData.senderGuId = userInfo.userId
    chatData.chatLogContent = inputRef.value.innerHTML

    isLoad.value.style.display = `block`
    e.target.disabled = true

      return new Promise((resolve, reject) => {
        chat(chatData)
        .then((res) => {
          if(res.code == 200){

            res.data.forEach(e => {
                msglogList.value.push(e)
            });
            msglogList.value.sort(sortData)
            inputRef.value.innerHTML = null
            imageShowRef.value.innerHTML = null

            nextTick()
            setTimeout(async () => {
                isShow.value.lastElementChild.scrollIntoView({ block: "end",behavior : "smooth" })
                isLoad.value.style.display = `none`
                e.target.disabled = false
            }, 50);

          } else{
            console.log('get error ', res)
          }
        })
        .catch((err) => {
          console.log('get error ', err)
          isLoad.value.style.display = `none`
          e.target.disabled = false
          modal.msgError('gpt请求失败');
          return
        })
      })
}


async function handelFix(e) {

    if (inputRef.value.innerHTML == false || inputRef.value.innerHTML == null) {
        modal.msgError('请输入内容');
        inputRef.value.innerHTML = null
        return
    }

    SocketCahtStore.chatOnlineUsers.forEach(function (item, index) {
        if (item.userGuid == userInfo.userId) {
            selfconnectionID = item.connectionId
        }
        if (item.userGuid == chatStore.chatUser.friendGuId) {
            connectionID = item.connectionId
        }
    })

    chatData.chatLogType = 1
    chatData.senderGuId = userInfo.userId
    chatData.chatLogContent = inputRef.value.innerHTML

    isLoad.value.style.display = `block`
    e.target.disabled = true

      return new Promise((resolve, reject) => {
        fix(chatData)
        .then((res) => {
          if(res.code == 200){
            res.data.forEach(e => {
                msglogList.value.push(e)
            });
            msglogList.value.sort(sortData)
            inputRef.value.innerHTML = null
            imageShowRef.value.innerHTML = null

            nextTick()
            setTimeout(async () => {
                isShow.value.lastElementChild.scrollIntoView({ block: "end",behavior : "smooth" })
                isShow.value.lastElementChild.style.color='#ff0000'
                isLoad.value.style.display = `none`
                e.target.disabled = false
            }, 50);
          } else{
            console.log('get error ', res)
          }
        })
        .catch((err) => {
          console.log('get error ', err)
          isLoad.value.style.display = `none`
          e.target.disabled = false
          modal.msgError('gpt请求失败');
          return
        })
      })
}


async function handelImages(e) {

    if (inputRef.value.innerHTML == false || inputRef.value.innerHTML == null) {
        modal.msgError('请输入内容');
        inputRef.value.innerHTML = null
        return
    }

    SocketCahtStore.chatOnlineUsers.forEach(function (item, index) {
        if (item.userGuid == userInfo.userId) {
            selfconnectionID = item.connectionId
        }
        if (item.userGuid == chatStore.chatUser.friendGuId) {
            connectionID = item.connectionId
        }
    })

    chatData.chatLogType = 1
    chatData.senderGuId = userInfo.userId
    chatData.chatLogContent = inputRef.value.innerHTML

    isLoad.value.style.display = `block`
    e.target.disabled = true

      return new Promise((resolve, reject) => {
        images(chatData)
        .then((res) => {
          if(res.code == 200){

            res.data.forEach(e => {
                msglogList.value.push(e)
            });
            msglogList.value.sort(sortData)
            inputRef.value.innerHTML = null
            imageShowRef.value.innerHTML = null

            nextTick()
            setTimeout(async () => {
                isShow.value.lastElementChild.scrollIntoView({ block: "end",behavior : "smooth" })

                isShow.value.lastElementChild.style.color='#ff0000'
                isLoad.value.style.display = `none`
                e.target.disabled = false
                if (res.data[1].images != null && res.data[1].images.length > 0) {
                    isShow.value.lastElementChild.firstElementChild.innerHTML = null
                }
                res.data[1].images.forEach(e => {
                   console.log(e)
                    isShow.value.lastElementChild.innerHTML += `<img style="width: 10rem!important;height: auto;" src=${e} alt="">`

                   // inputRef.value.innerHTML += `<img style="width: 10rem!important;height: auto;" src=${msg.value} alt="">`
                });

            }, 50);
          } else{
            console.log('get error ', res)
          }
        })
        .catch((err) => {
          console.log('get error ', err)
          isLoad.value.style.display = `none`
          e.target.disabled = false
          modal.msgError('gpt请求失败');
          return
        })
      })
}




function handleColse() {
    inputRef.value.innerHTML = null

    switchStore.isChat = false
    switchStore.isGroupChat = false
}

function toBottom() {
  const listContainer = this.$refs["chatbox"]
  listContainer.scrollTop = listContainer.scrollHeight
}


const handleImgSuccess = (value) => {
        console.log("handleImgSuccess" + value.data);
    if (value.code != 200) {
        modal.msgError(value.data);
        return;
    }
    modal.msgSuccess('上传成功');
    console.log(value.data);

    msg.value = value.data;
    imageShowRef.value.innerHTML += `<img style="width: 6rem!important;height: auto;" src=${msg.value} alt="">`
    chatData.images.push(msg.value)
     console.log(chatData, "shusdasd");
};

function handleEntry(event) {
    let e = event.keyCode
//    if (event.shiftKey && e === 13) {
//    } else if (e === 13) {
//        event.preventDefault()
//        handelSend(event)
//    }
}

function handleGroupEntry(event) {
    let e = event.keyCode
    if (event.shiftKey && e === 13) {
    } else if (e === 13) {
        event.preventDefault()
        handelGroupSend()
    }
}

function hanleChatHistoryLog() {
    if (switchStore.isChat) {
        isAdd.value = true
        getListData.value.FriendsGuId = chatStore.chatUser.friendGuId
        getfriendList()
    }
}




function sortData(a, b) {
    return new Date(a.chatLogSendTime).getTime() - new Date(b.chatLogSendTime).getTime()
}

createMediaList({
    1100(ctx) {
        if(ctx.matches){
            tcWidth.value = "100%"
        }
        else{
            tcWidth.value = "50%"
        }
    },
})

function createMediaList(opt) {
        for (let optKey in opt) {
            let mediaCtx = window !== undefined ? window.matchMedia(`(max-width: ${optKey}px)`) : global.matchMedia(`(max-width: ${optKey}px)`)
            if (mediaCtx?.matches) {
                opt[optKey](mediaCtx)
            }
            mediaCtx.addListener(opt[optKey])
        }
}



//chat ------------------------------------------------------------------------------------------- start


import { onMounted, ref, watch, computed, nextTick, reactive } from 'vue'




const chatbox = ref();
let msgList = ref([])
let ortherMsgList = ref([])
let isOnline = ref(false);

const postDacta = reactive({
    UserGuId: userStore.userinfo.chatUserGuId
})

watch(() => chatStore.chatUser, async (value) => {
    isOnline.value = false
    msgList.value.length = 0;
    //getListData.value.pageNum = 1
    isMore.value = false

    SocketCahtStore.chatOnlineUsers.forEach(function (item, index) {
        // console.log(item);
        if (item.userGuid == chatStore.chatUser.friendGuId) {
            isOnline.value = true
            // console.log(selfconnectionID,'自己的连接Id');
        }
    })


    selfMsgList.value.length = 0
    if (msglogList.value.length != 0) {
        msglogList.value.length = 0
    }

    if (isOnline.value) {
        // await signalr.init(import.meta.env.VITE_APP_SOCKET_API)
        signalr.SR.off("ReceiveMessage")

        signalr.SR.on("ReceiveMessage", data => {
            msgList.value.push(data);
            chatStore.getList(postDacta)
        });
    }


    if (switchStore.isChat) {
        getListData.value.FriendsGuId = chatStore.chatUser.friendGuId
        getListData.value.ChatLogType = 1
    }


    nextTick(() => {
        setTimeout(async () => {

        }, 100);
        setTimeout(() => {
            isShow.value.scrollIntoView({ block: "end" })
            isLoad.value.style.display = `none`
        }, 100);
    })


},
    {
        immediate: true,
        deep: true
    })



function handleimg(v) {
    switchStore.isFriendIntro = true
    switchStore.isPersonal = false
    switchStore.isFriends = false
    switchStore.isChat = false
    switchStore.isGroupChat = false

    switchStore.FriendIntroData = v
}



</script>

<style lang="scss" scoped>

.container {
    width: 100%;
    animation: ani 1s;
}

@keyframes ani {
    0% {
        transform: translateX(600px);
    }

    100% {
        transform: translateX(0px);
    }
}

.nullBox {
    width: 95.5%;
    height: 300px;
    /* background-color: red; */
    border-bottom: rgba(121, 163, 159, 0.3) 1px solid;
    overflow-y: scroll;
    overflow: auto;
    padding: 15px;
    display: flex;
    justify-content: center;
}
.nulltetx {
    margin-left: 90px;
    margin-top: 10px;
}


// send -- start
.toolbar {
    width: 97%;
    height: 35px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 10px;
}

.tools {
    display: flex;
    height: 1.5rem;
    align-items: center;
}

.tool,
.tools img {
    margin: 0 5px;
    width: 1.5rem;
    cursor: pointer;
}

.input_send {
    width: 99%;
    height: 75px;
    border: 0;
    background: none;
    /* outline: none; */
}

.history img {
    width: 1.7rem;
    cursor: pointer;
}

.history img:hover {
    background-color: rgba(216, 216, 216, 0.4);
    transition: .3s;
    border-radius: 5px;
}

.send_box {}

/* .send_box::-webkit-scrollbar { width: 0; height: 0; color: transparent; } */
.textarea {
    overflow-x: hidden;
    box-sizing: border-box;
    height: 120px;
    width: 100%;
    outline: none;
    border: none;
    padding: 0 10px;
    border: 0;
    border-radius: 5px;
    background: none;
    font-size: 1rem;
    padding: 10px;
    resize: none;

    img {
        width: 50px;
    }
}

.btn_box {
    display: flex;
    justify-content: flex-end;

    button {
        padding: 10px;
        border: 0;
        background-color: #b0e1f8;
        border-radius: 5px;
        margin: 0 8px;
        cursor: pointer;
    }
}

.chat_img_send {
    width: 1rem !important;
    height: auto;
}

.historyBox {
    margin-top: 2rem;
    width: 100%;
    height: 100%;

    ._message {
        width: 100%;
        height: auto;
        display: flex;
        align-items: center;
        border-bottom: #f5f5f5 1px solid;
        position: relative;
        margin: 1rem 0;
        padding: 10px 0;

        .imgbox {
            display: flex;
            margin: 0 15px 0 0;
            align-items: center;
        }

        .imgbox img {
            width: 2.5rem;
            height: 2.5rem;
            border-radius: 10px;
            object-fit: cover;
        }

        ._message_content_box {
            width: 64%;

            ._message_name {
                font-size: 0.3rem;
                margin-bottom: 4px;
                color: rgb(121, 163, 159);
            }
        }

        ._message_time {
            position: absolute;
            right: 0;
            color: rgb(121, 163, 159);
            font-size: 0.3rem;
        }

    }
}

.more {
    width: auto;
    display: flex;
    justify-content: center;
    cursor: pointer;
    margin: 0.5rem 0;
}



// send -- end



// chat -- start
.chat_list {
    width: 98%;
    height: 430px;
    /* background-color: red; */
    border-bottom: rgba(121, 163, 159, 0.3) 1px solid;
    overflow-y: scroll;
    /* overflow: auto; */
    padding: 15px;
}

/* .chat_list::-webkit-scrollbar { width: 0; height: 0; color: transparent; } */

.chat_other_box {
    display: flex;
    margin-bottom: 20px;
}

.chat_self_box {
    display: flex;
    margin-bottom: 20px;
    justify-content: flex-end;
}

.chat_img_box {
    width: 45px;
    height: 45px;
    border-radius: 50%;
    background-color: #fff;
    cursor: pointer;
}

.chat_img {
    width: 45px;
    height: 45px;
    object-fit: cover;
    border-radius: 50%;
}

.message_box {
    padding: 10px;
    width: auto;
    height: auto;
    background-color: #e5e5e5;
    margin-top: 11px;
    margin-left: 5px;
    margin: 10px 5px;
    border-radius: 10px;
    word-break: break-all;
    white-space: break-spaces;
}

.message_self_box {
    padding: 10px;
    width: auto;
    height: auto;
    margin-top: 10px;
    margin-left: 5px;
    margin: 10px 5px;
    border-radius: 10px;
    background-color: #8a98c9 !important;
    word-break: break-all;
}

.more {
    width: auto;
    display: flex;
    justify-content: center;
    cursor: pointer;
    margin: 0.5rem 0;
}

.loader_box {
    position: fixed;
    height: 100%;
    left: 45%;
}

.loader span {
    width: 100px !important;
    height: 100px !important;
    transition: 0.3s;
}
// chat -- end
</style>