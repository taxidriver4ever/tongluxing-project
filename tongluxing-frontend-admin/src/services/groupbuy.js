import { apiRequest } from './apiClient.js'
export const getGroupbuys=({status='',page=1,size=50}={})=>apiRequest(`/v1/admin/groupbuys?status=${encodeURIComponent(status)}&page=${page}&size=${size}`)
export const getGroupbuy=id=>apiRequest(`/v1/admin/groupbuys/${id}`)
export const interveneGroupbuy=(id,data)=>apiRequest(`/v1/admin/groupbuys/${id}/intervene`,{method:'POST',body:JSON.stringify(data)})
