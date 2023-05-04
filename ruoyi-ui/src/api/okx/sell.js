import request from '@/utils/request'

// 查询参数列表
export function listSell(query) {
  return request({
    url: '/okx/sell/list',
    method: 'get',
    params: query
  })
}

// 查询参数详细
export function getSell(id) {
  return request({
    url: '/okx/sell/' + id,
    method: 'get'
  })
}

// 根据参数键名查询参数值
export function getSellKey(sell) {
  return request({
    url: '/okx/sell/' + sell,
    method: 'get'
  })
}


// 修改参数配置
export function updateSell(data) {
  return request({
    url: '/okx/sell',
    method: 'put',
    data: data
  })
}

// 删除参数配置
export function delSell(id) {
  return request({
    url: '/okx/sell/' + id,
    method: 'delete'
  })
}

