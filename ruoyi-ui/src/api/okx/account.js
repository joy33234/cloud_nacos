import request from '@/utils/request'

// 查询参数列表
export function listAccount(query) {
  return request({
    url: '/okx/account/list',
    method: 'get',
    params: query
  })
}

// 查询参数详细
export function getAccount(id) {
  return request({
    url: '/okx/account/' + id,
    method: 'get'
  })
}

// 根据参数键名查询参数值
export function getAccountKey(configKey) {
  return request({
    url: '/okx/account/configKey/' + configKey,
    method: 'get'
  })
}

// 新增参数配置
export function addAccount(data) {
  return request({
    url: '/okx/account',
    method: 'post',
    data: data
  })
}

// 修改参数配置
export function updateAccount(data) {
  return request({
    url: '/okx/account',
    method: 'put',
    data: data
  })
}

// 删除参数配置
export function delAccount(id) {
  return request({
    url: '/okx/account/' + id,
    method: 'delete'
  })
}

// 刷新参数缓存
export function refreshCache() {
  return request({
    url: '/okx/account/refreshCache',
    method: 'delete'
  })
}

export function changeUserStatus(id, status) {
  const data = {
    id,
    status
  }
  return request({
    url: '/okx/account/changeStatus',
    method: 'put',
    data: data
  })
}
