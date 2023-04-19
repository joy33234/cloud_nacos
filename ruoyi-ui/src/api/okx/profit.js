import request from '@/utils/request'

// 查询参数列表
export function list(query) {
  return request({
    url: '/okx/account/profit/detail',
    method: 'get',
    params: query
  })
}

// 查询参数列表
export function profit(query) {
  return request({
    url: '/okx/account/profit',
    method: 'get',
    params: query
  })
}


