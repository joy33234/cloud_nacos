import request from '@/utils/request'

// 查询参数列表
export function listCoin(query) {
  return request({
    url: '/okx/coin/list',
    method: 'get',
    params: query
  })
}

// 查询参数详细
export function getCoin(id) {
  return request({
    url: '/okx/coin/' + id,
    method: 'get'
  })
}

// 根据参数键名查询参数值
export function getCoinKey(coin) {
  return request({
    url: '/okx/coin/' + coin,
    method: 'get'
  })
}

// 新增参数配置
export function addCoin(data) {
  return request({
    url: '/okx/coin',
    method: 'post',
    data: data
  })
}

// 修改参数配置
export function updateCoin(data) {
  return request({
    url: '/okx/coin',
    method: 'put',
    data: data
  })
}

// 删除参数配置
export function delCoin(id) {
  return request({
    url: '/okx/coin/' + id,
    method: 'delete'
  })
}

// 刷新参数缓存
export function refreshCache() {
  return request({
    url: '/okx/coin/refreshCache',
    method: 'delete'
  })
}
