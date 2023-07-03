import request from '../utils/request'

// 登录方法
export function login(username, password, needcode) {
  const data = {
    username,
    password,
    needcode
  }
  return request({
    url: '/auth/login',
    headers: {
      isToken: false
    },
    method: 'POST',
    data: data,
  })
}


//// 登录方法 -- ui
//export function login(username, password, code, uuid) {
//  return request({
//    url: '/auth/login',
//    headers: {
//      isToken: false
//    },
//    method: 'post',
//    data: { username, password, code, uuid }
//  })
//}



// 修改用户信息
export function updateChatUser(data) {
  return request({
    url: '/forehead/user/update',
    method: 'put',
    data: data
  })
}

// 获取用户详细信息
export function getInfo(username) {
  const data = {
    username
  }
  return request({
    url: '/system/user/getinfo',
    method: 'get',
    data: data
  })
}


//export function getInfo(username) {
//  const data = {
//    username
//  }
//  return request({
//    url: '/system/user/getInfo',
//    method: 'get',
//    data: data
//  })
//}


//// 获取用户详细信息 -- ui
//export function getInfo() {
//  return request({
//    url: '/system/user/getInfo',
//    method: 'get'
//  })
//}

// 退出方法
export function logout() {
  return request({
    url: '/auth/logout',
    method: 'delete'
  })
}


/**
 * 注册
 * @returns 
 */
export function register(nickName,username, password) {
  const data = {
    nickName,
    username,
    password
  }
  return request({
    url: '/auth/register',
    method: 'post',
    data: data
  })
}

// 刷新方法
export function refreshToken() {
  return request({
    url: '/auth/refresh',
    method: 'post'
  })
}

