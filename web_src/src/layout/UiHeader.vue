<template>
  <div id="UiHeader">

    <el-menu router :default-active="activeIndex" menu-trigger="click" background-color="#001529" text-color="#fff"
             active-text-color="#1890ff" mode="horizontal">

      <el-menu-item index="/console">控制台</el-menu-item>
      <el-menu-item index="/live">分屏监控</el-menu-item>
<!--      <el-menu-item index="/map">电子地图</el-menu-item>-->
      <el-menu-item index="/deviceList">国标设备</el-menu-item>
      <el-menu-item index="/streamPushList">推流列表</el-menu-item>
      <el-menu-item index="/streamProxyList">拉流代理</el-menu-item>
      <el-submenu index="/channel">
        <template slot="title">通道管理</template>
        <el-menu-item index="/channel/region">行政区划</el-menu-item>
        <el-menu-item index="/channel/group">业务分组</el-menu-item>
      </el-submenu>
      <el-menu-item index="/recordPlan">录制计划</el-menu-item>
      <el-menu-item index="/cloudRecord">云端录像</el-menu-item>
      <el-menu-item index="/mediaServerManger">节点管理</el-menu-item>
      <el-menu-item index="/platformList/15/1">国标级联</el-menu-item>
      <el-menu-item v-if="editUser" index="/userManager">用户管理</el-menu-item>
      <el-menu-item index="/operations">运维中心</el-menu-item>

      <!--            <el-submenu index="/setting">-->
      <!--              <template slot="title">系统设置</template>-->
      <!--              <el-menu-item index="/setting/web">WEB服务</el-menu-item>-->
      <!--              <el-menu-item index="/setting/sip">国标服务</el-menu-item>-->
      <!--              <el-menu-item index="/setting/media">媒体服务</el-menu-item>-->
      <!--            </el-submenu>-->
      <!--            <el-menu-item style="float: right;" @click="loginout">退出</el-menu-item>-->
      <el-submenu index="" style="float: right;">
        <template slot="title">欢迎，{{ username }}</template>
        <el-menu-item @click="openDoc">在线文档</el-menu-item>
        <el-menu-item>
          <el-switch v-model="alarmNotify" inactive-text="报警信息推送" @change="alarmNotifyChannge"></el-switch>
        </el-menu-item>
        <el-menu-item @click="changePassword">修改密码</el-menu-item>
        <el-menu-item @click="loginout">注销</el-menu-item>
      </el-submenu>
    </el-menu>
    <changePasswordDialog ref="changePasswordDialog"></changePasswordDialog>
  </div>
</template>

<script>
import changePasswordDialog from '../components/dialog/changePassword.vue'
import userService from '../components/service/UserService'
import {Notification} from 'element-ui';

export default {
  name: "UiHeader",
  components: {Notification, changePasswordDialog},
  data() {
    return {
      alarmNotify: false,
      sseSource: null,
      username: userService.getUser().username,
      activeIndex: this.$route.path.indexOf("/", 1)>0?this.$route.path.substring(0, this.$route.path.indexOf("/", 1)):this.$route.path,
      editUser: userService.getUser() ? userService.getUser().role.id === 1 : false
    };
  },
  created() {
    console.log(34334)
    console.log(this.$route.path)
    console.log(this.$route.path.indexOf("/", 1))
    console.log(this.activeIndex)
    if (this.$route.path.startsWith("/channelList")) {
      this.activeIndex = "/deviceList"
    }
  },
  mounted() {
    window.addEventListener('beforeunload', e => this.beforeunloadHandler(e))
    this.alarmNotify = this.getAlarmSwitchStatus() === "true";

    // TODO: 此处延迟连接 sse, 避免 sse 连接时 browserId 还未生成, 后续待优化
    setTimeout(() => {
      this.sseControl()
    }, 3000);
  },
  methods: {
    loginout() {
      this.$axios({
        method: 'get',
        url: "/api/user/logout"
      }).then((res) => {
        // 删除用户信息，回到登录页面
        userService.clearUserInfo()
        this.$router.push('/login');
        if (this.sseSource != null) {
          this.sseSource.close();
        }

      }).catch((error) => {
        console.error("登出失败")
        console.error(error)
      });
    },
    changePassword() {
      this.$refs.changePasswordDialog.openDialog()
    },
    openDoc() {
      console.log(process.env.BASE_API)
      window.open(!!process.env.BASE_API ? process.env.BASE_API + "/doc.html" : "/doc.html")
    },
    beforeunloadHandler() {
      this.sseSource.close();
    },
    alarmNotifyChannge() {
      this.setAlarmSwitchStatus()
      this.sseControl()
    },
    sseControl() {
      let that = this;
      if (this.alarmNotify) {
        console.log("申请SSE推送API调用，浏览器ID: " + this.$browserId);
        let url = (process.env.NODE_ENV === 'development' ? "debug" : "") + 'api/emit?browserId=' + this.$browserId
        this.sseSource = new EventSource(url);
        this.sseSource.addEventListener('message', function (evt) {
          console.log("收到信息：" + evt.data);
          let data = JSON.parse(evt.data)
          that.$notify({
            title: '报警信息',
            dangerouslyUseHTMLString: true,
            message: `<strong>设备名称：</strong> <i> ${data.deviceName}</i>` +
                     `<br><strong>设备编号：</strong> <i>${ data.deviceId}</i>` +
                     `<br><strong>通道编号：</strong> <i>${ data.channelId}</i>` +
                     `<br><strong>报警级别：</strong> <i>${ data.alarmPriorityDescription}</i>` +
                     `<br><strong>报警方式：</strong> <i>${ data.alarmMethodDescription}</i>` +
                     `<br><strong>报警类型：</strong> <i>${ data.alarmTypeDescription}</i>` +
                     `<br><strong>报警时间：</strong> <i>${ data.alarmTime}</i>`,
            type: 'warning',
            position: 'bottom-right',
            duration: 5000
          });
        });
        this.sseSource.addEventListener('open', function (e) {
          console.log("SSE连接打开.");
        }, false);
        this.sseSource.addEventListener('error', function (e) {
          if (e.target.readyState == EventSource.CLOSED) {
            console.log("SSE连接关闭");
          } else {
            console.log(e.target.readyState);
          }
        }, false);
      } else {
        if (this.sseSource != null) {
          this.sseSource.removeEventListener('open', null);
          this.sseSource.removeEventListener('message', null);
          this.sseSource.removeEventListener('error', null);
          this.sseSource.close();
        }

      }
    },
    getAlarmSwitchStatus() {
      if (localStorage.getItem("alarmSwitchStatus") == null) {
        localStorage.setItem("alarmSwitchStatus", false);
      }
      return localStorage.getItem("alarmSwitchStatus");
    },
    setAlarmSwitchStatus() {
      localStorage.setItem("alarmSwitchStatus", this.alarmNotify);
    }
  },
  destroyed() {
    window.removeEventListener('beforeunload', e => this.beforeunloadHandler(e))
    if (this.sseSource != null) {
      this.sseSource.removeEventListener('open', null);
      this.sseSource.removeEventListener('message', null);
      this.sseSource.removeEventListener('error', null);
      this.sseSource.close();
    }
  },

}

</script>
<style>
#UiHeader .el-switch__label {
  color: white;
}

.el-menu--popup .el-menu-item .el-switch .el-switch__label {
  color: white !important;
}

#UiHeader .el-switch__label.is-active {
  color: #409EFF;
}

#UiHeader .el-menu-item.is-active {
  color: #fff !important;
  background-color: #1890ff !important;
}
#UiHeader .el-submenu.is-active {
  background-color: #1890ff !important;
}
#UiHeader .el-submenu.is-active .el-submenu__title {
  color: #fff !important;
  background-color: #1890ff !important;
}
#UiHeader .el-submenu.is-active .el-submenu__icon-arrow {
  color: #fff !important;
}
</style>
