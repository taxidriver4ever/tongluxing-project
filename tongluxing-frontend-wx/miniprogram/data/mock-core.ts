export interface CoreTrip {
  id: string
  title: string
  start: string
  end: string
  date: string
  time: string
  distance: string
  duration: string
  members: number
  capacity: number
  status: "active" | "upcoming" | "completed"
}

export interface CoreTeam {
  id: string
  name: string
  route: string
  overlap: number
  members: number
  capacity: number
  notice: string
}

export interface CoreConversation {
  id: string
  title: string
  preview: string
  time: string
  unread: number
  avatar: string
  team: boolean
}

export const mockUser = {
  id: 10001,
  nickname: "林晓雨",
  phone: "138****8888",
  city: "成都",
  level: "Lv.3 同路先锋",
  points: 2360,
  nextLevelPoints: 640,
  vehicle: "理想 L7 · 川A·8X6P2",
  bio: "喜欢周末自驾，也喜欢认识新的同路人。"
}

export const mockTrips: CoreTrip[] = [
  { id: "T1001", title: "成都到四姑娘山", start: "成都·锦江", end: "四姑娘山景区", date: "7月20日", time: "08:30", distance: "218 km", duration: "3小时42分", members: 5, capacity: 8, status: "active" },
  { id: "T1002", title: "川西小环线周末游", start: "成都东站", end: "新都桥", date: "7月27日", time: "07:00", distance: "336 km", duration: "5小时18分", members: 3, capacity: 6, status: "upcoming" },
  { id: "T1003", title: "青城山避暑同行", start: "成都·高新", end: "青城后山", date: "8月03日", time: "09:00", distance: "78 km", duration: "1小时26分", members: 4, capacity: 5, status: "upcoming" }
]

export const mockTeams: CoreTeam[] = [
  { id: "TEAM01", name: "川西慢游车队", route: "成都 → 四姑娘山", overlap: 92, members: 5, capacity: 8, notice: "保持车距，途中在映秀服务区集合。" },
  { id: "TEAM02", name: "周末出逃计划", route: "成都 → 青城后山", overlap: 86, members: 4, capacity: 6, notice: "轻松自驾，不赶路，欢迎新手司机。" }
]

export const mockConversations: CoreConversation[] = [
  { id: "CHAT01", title: "川西慢游车队", preview: "队长：明早 8:20 在东门集合", time: "09:41", unread: 3, avatar: "川", team: true },
  { id: "CHAT02", title: "陈屿", preview: "我已经把新的途经点发到群里了", time: "昨天", unread: 0, avatar: "陈", team: false },
  { id: "CHAT03", title: "周末出逃计划", preview: "欢迎新成员加入车队", time: "周一", unread: 1, avatar: "周", team: true }
]

export const mockMessages = [
  { id: 1, mine: false, avatar: "峰", sender: "队长阿峰", content: "大家明早 8:20 在东门集合，8:30 准时出发。", time: "09:32" },
  { id: 2, mine: true, avatar: "我", sender: "我", content: "收到，我会提前到。", time: "09:35" },
  { id: 3, mine: false, avatar: "陈", sender: "陈屿", content: "映秀服务区作为第一个休息点怎么样？", time: "09:38" }
]

export const mockVehicles = [
  { id: "V1001", brand: "理想", model: "L7 Max", plate: "川A·8X6P2", color: "星环灰", seats: 5, default: true, certified: true },
  { id: "V1002", brand: "大众", model: "途观L", plate: "川A·26M8Q", color: "极光白", seats: 5, default: false, certified: false }
]

export const mockBadges = [
  { code: "FIRST_TRIP", name: "初次同行", desc: "完成第一次同行", earned: true, mark: "01" },
  { code: "MILEAGE_1000", name: "千里同行", desc: "累计同行1000公里", earned: true, mark: "1K" },
  { code: "CAPTAIN", name: "可靠队长", desc: "带队完成5次行程", earned: true, mark: "队" },
  { code: "HELPFUL", name: "热心车友", desc: "帮助3位同路人", earned: false, mark: "助" },
  { code: "CROSS_CITY", name: "跨城探索", desc: "到访10座城市", earned: false, mark: "城" },
  { code: "MILEAGE_10000", name: "万里征途", desc: "累计同行10000公里", earned: false, mark: "10K" }
]


export const mockFriendRequests = [
  { id: "F01", avatar: "王", name: "王子轩", note: "来自川西慢游车队", accepted: false },
  { id: "F02", avatar: "唐", name: "唐佳宁", note: "通过同行成员添加", accepted: false }
]

export const mockWaypoints = [
  { id: "W01", name: "映秀服务区", meta: "距起点 78 km · 休息20分钟" },
  { id: "W02", name: "巴朗山观景台", meta: "距起点 164 km · 休息15分钟" }
]

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

export function getMockTrips(): Promise<CoreTrip[]> { return Promise.resolve(clone(mockTrips)) }
export function getMockTeams(): Promise<CoreTeam[]> { return Promise.resolve(clone(mockTeams)) }
export function getMockConversations(): Promise<CoreConversation[]> { return Promise.resolve(clone(mockConversations)) }
export function getMockTrip(id: string): Promise<CoreTrip> { return Promise.resolve(clone(mockTrips.find(item => item.id === id) || mockTrips[0])) }
export function getMockTeam(id: string): Promise<CoreTeam> { return Promise.resolve(clone(mockTeams.find(item => item.id === id) || mockTeams[0])) }
