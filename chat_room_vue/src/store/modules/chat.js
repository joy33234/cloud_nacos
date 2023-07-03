import { defineStore,storeToRefs} from 'pinia'
import useSwitchStore from './switch'
import { getChatLogList } from '../../api/chatLog/chatLog'

const switchStore = useSwitchStore()

const useChatStore = defineStore('chat', {
    state: () => {
        return {
            chatUser: null,
            chatGroup: null,
            chatLogList: [],
        }
    },
    actions: {

      

    },
    getters: {

    }
})

export default useChatStore;