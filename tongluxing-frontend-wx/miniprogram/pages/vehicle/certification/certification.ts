import { getMyVehicles, getVehicleAuthEligibility, getVehicleAuthStatus, submitVehicleAuth } from "../../../api/vehicle"
import { ImageBizType, uploadImage } from "../../../api/storage"

interface MaterialItem { label:string; group:"registrationLicenseImages"|"vehicleImages"; index:number; bizType:ImageBizType; required:boolean }
const MATERIALS: MaterialItem[] = [
  { label:"行驶证主页", group:"registrationLicenseImages", index:0, bizType:"VEHICLE_LICENSE_FRONT", required:true },
  { label:"行驶证副页", group:"registrationLicenseImages", index:1, bizType:"VEHICLE_LICENSE_BACK", required:true },
  { label:"车辆正面照", group:"vehicleImages", index:0, bizType:"VEHICLE_PHOTO_FRONT", required:true },
  { label:"车辆侧面照", group:"vehicleImages", index:1, bizType:"VEHICLE_PHOTO_SIDE", required:false },
  { label:"车辆后方照", group:"vehicleImages", index:2, bizType:"VEHICLE_PHOTO_REAR", required:false }
]

Component({
  data: {
    loading:false,
    savingLabel:"",
    status:"UNSUBMITTED",
    reason:"",
    submitTime:"",
    form:{ vehicleBrand:"", vehicleModel:"", plateNumber:"", vehicleColor:"" },
    registrationLicenseImages:["", ""],
    vehicleImages:["", "", ""]
  },
  lifetimes:{ attached(){ void this.loadPage() } },
  methods:{
    async loadPage(){
      this.setData({loading:true})
      try{
        const [status,vehicles]=await Promise.all([getVehicleAuthStatus(),getMyVehicles()])
        const vehicle=vehicles.vehicles&&vehicles.vehicles.length?vehicles.vehicles[0]:null
        this.setData({
          status:status.status||"UNSUBMITTED", reason:status.reason||"", submitTime:status.submitTime||"",
          "form.vehicleBrand":vehicle?vehicle.brand:"", "form.vehicleModel":vehicle?vehicle.model:"",
          "form.plateNumber":"", "form.vehicleColor":vehicle?vehicle.color:""
        })
      }catch(error){wx.showToast({title:this.getErrorMessage(error),icon:"none"})}
      finally{this.setData({loading:false})}
    },
    onInput(event:WechatMiniprogram.Input){const field=String(event.currentTarget.dataset.field||"");if(field)this.setData({["form."+field]:event.detail.value})},
    chooseImage(event:WechatMiniprogram.TouchEvent){
      if(this.data.status==="PENDING"||this.data.status==="PASS"||this.data.loading)return
      const group=String(event.currentTarget.dataset.group) as MaterialItem["group"]
      const index=Number(event.currentTarget.dataset.index)
      wx.chooseMedia({count:1,mediaType:["image"],sourceType:["album","camera"],success:result=>{const file=result.tempFiles&&result.tempFiles[0];if(file)this.setData({[group+"["+index+"]"]:file.tempFilePath})}})
    },
    async onSubmit(){
      if(this.data.loading)return
      if(this.data.status==="PENDING"){wx.showToast({title:"车辆认证审核中",icon:"none"});return}
      if(this.data.status==="PASS"){wx.showToast({title:"车辆已经认证",icon:"none"});return}
      const form={...this.data.form,plateNumber:this.data.form.plateNumber.trim().replace(/\s/g,"").toUpperCase(),vehicleBrand:this.data.form.vehicleBrand.trim(),vehicleModel:this.data.form.vehicleModel.trim(),vehicleColor:this.data.form.vehicleColor.trim()}
      if(!form.vehicleBrand||!form.vehicleModel||!form.plateNumber||!form.vehicleColor){wx.showToast({title:"请完善车辆基础信息",icon:"none"});return}
      if(!this.data.registrationLicenseImages[0]||!this.data.registrationLicenseImages[1]||!this.data.vehicleImages[0]){wx.showToast({title:"请上传两张行驶证和车辆正面照",icon:"none"});return}
      this.setData({loading:true,savingLabel:"检查车辆信息…"})
      try{
        const eligibility=await getVehicleAuthEligibility(form.plateNumber)
        if(!eligibility.eligible)throw new Error(eligibility.reason||"该车辆不能再次提交认证")
        const uploadedRegistration=["",""]
        const uploadedVehicle=["","",""]
        for(const material of MATERIALS){
          const path=material.group==="registrationLicenseImages"?this.data.registrationLicenseImages[material.index]:this.data.vehicleImages[material.index]
          if(!path)continue
          this.setData({savingLabel:"正在上传"+material.label+"…"})
          const key=await uploadImage(path,material.bizType,form.plateNumber)
          if(material.group==="registrationLicenseImages")uploadedRegistration[material.index]=key;else uploadedVehicle[material.index]=key
        }
        this.setData({savingLabel:"正在提交认证…"})
        const result=await submitVehicleAuth({...form,registrationLicenseImages:uploadedRegistration,vehicleImages:uploadedVehicle.filter(Boolean)})
        this.setData({status:result.status,reason:result.reason||"",submitTime:result.submitTime||""})
        wx.showToast({title:"认证材料已提交",icon:"success"})
      }catch(error){wx.showToast({title:this.getErrorMessage(error),icon:"none"})}
      finally{this.setData({loading:false,savingLabel:""})}
    },
    getErrorMessage(error:unknown):string{return error instanceof Error?error.message:"网络异常，请稍后重试"}
  }
})
