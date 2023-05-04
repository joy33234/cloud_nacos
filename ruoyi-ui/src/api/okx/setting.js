import request from '@/utils/request'

// 查询参数列表
export function listSetting(query) {
  return request({
    url: '/okx/setting/list',
    method: 'get',
    params: query
  })
}

// 查询参数详细
export function getSetting(configId) {
  return request({
    url: '/okx/setting/' + configId,
    method: 'get'
  })
}

// 根据参数键名查询参数值
export function getSettingKey(configKey) {
  return request({
    url: '/okx/setting/configKey/' + configKey,
    method: 'get'
  })
}

// 新增参数配置
export function addSetting(data) {
  return request({
    url: '/okx/setting',
    method: 'post',
    data: data
  })
}

// 修改参数配置
export function updateSetting(data) {
  return request({
    url: '/okx/setting',
    method: 'put',
    data: data
  })
}

// 删除参数配置
export function delSetting(configId) {
  return request({
    url: '/okx/setting/' + configId,
    method: 'delete'
  })
}

// 刷新参数缓存
export function refreshCache() {
  return request({
    url: '/okx/setting/refreshCache',
    method: 'delete'
  })
}
