import request from '@/utils/request'

// 查询参数列表
export function listBuy(query) {
  return request({
    url: '/okx/buy/list',
    method: 'get',
    params: query
  })
}

// 查询参数详细
export function getBuy(id) {
  return request({
    url: '/okx/buy/' + id,
    method: 'get'
  })
}

// 根据参数键名查询参数值
export function getBuyKey(buy) {
  return request({
    url: '/okx/buy/' + buy,
    method: 'get'
  })
}


// 修改参数配置
export function updateBuy(data) {
  return request({
    url: '/okx/buy',
    method: 'put',
    data: data
  })
}

// 删除参数配置
export function delBuy(id) {
  return request({
    url: '/okx/buy/' + id,
    method: 'delete'
  })
}

