import request from '../../utils/request'

// 聊天记录列表
export function getChatLogList(data) {
  return request({
    url: '/business/ChatLog/chatLogList',
    method: 'post',
    data: data
  })
}

// 聊天好友记录列表
export function chatFriendLogList(data) {
  return request({
    url: '/business/ChatLog/chatFriendLogList',
    method: 'post',
    data: data
  })
}

// 聊天群聊记录列表
export function chatGroupLogList(data) {
  return request({
    url: '/business/ChatLog/chatGroupLogList',
    method: 'post',
    data: data
  })
}

// 聊天好友历史记录列表
export function chatFriendLogHistoryList(data) {
  return request({
    url: '/business/ChatLog/chatFriendLogHistoryList',
    method: 'post',
    data: data
  })
}

// 聊天群聊历史记录列表
export function chatGroupLogHistoryList(data) {
  return request({
    url: '/business/ChatLog/chatGroupLogHistoryList',
    method: 'post',
    data: data
  })
}

// 获取自己发的消息(最新)
export function slefNewMsg(data) {
  return request({
    url: '/business/ChatLog/slefNewMsg',
    method: 'post',
    data: data
  })
}

// 已读处理
export function read(data) {
  return request({
    url: '/business/ChatLog/read',
    method: 'post',
    data: data
  })
}



// 获取聊天信息
export function chat(data) {
  return request({
    url: '/pandora/ai/chat',
    method: 'post',
    data: data
  })
}


// 语法校验
export function fix(data) {
  return request({
    url: '/pandora/ai/fix',
    method: 'post',
    data: data
  })
}

// 生成图片
export function images(data) {
  return request({
    url: '/pandora/ai/images',
    method: 'post',
    data: data
  })
}
