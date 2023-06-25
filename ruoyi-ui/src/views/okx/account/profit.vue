<template>
  <div class="app-container">



    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="120px">
      <el-form-item label="币种" prop="coin">
        <el-input
          v-model="queryParams.coin"
          placeholder="请输入参数币种"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>


      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
      </br>
      <el-form-item label="帐号名称" prop="dictName">
        {{ profitDto.accountName }}
      </el-form-item>
      <el-form-item label="总利润" prop="dictName">
        {{ profitDto.profit }}
      </el-form-item>
      <el-form-item label="完成利润" prop="dictName">
        {{ profitDto.finishProfit }}
      </el-form-item>
      <el-form-item label="未完成利润" prop="dictName">
        {{ profitDto.unFinishProfit }}
      </el-form-item>
    </el-form>



    <el-table v-loading="loading" :data="coinList" >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="coin" prop="coin" :show-overflow-tooltip="true" />
      <el-table-column label="利润" prop="profit" :show-overflow-tooltip="true" />
      <el-table-column label="可用余额" prop="balance" :show-overflow-tooltip="true" />
      <el-table-column label="最小单位" prop="unit" :show-overflow-tooltip="true" />
      <el-table-column label="备注" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.updateTime) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

  </div>
</template>

<script>
import { list, profit } from "@/api/okx/profit";

export default {
  name: "AccountProfit",
  data() {
    return {
      // 遮罩层
      loading: false,
      // 选中用户组
      userIds: [],
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 用户表格数据
      coinList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        accountId: undefined,
        userName: undefined,
        coin: undefined
      },
      profitDto:{
        accountName:"",
        profit:0,
        finishProfit : 0,
        unFinishProfit:0
      }
    };
  },
  created() {
      this.queryParams.accountId = this.$route.query.accountId;
      this.getList();
      this.getProfit();
  },
  methods: {
    /** 查询币种利润列表 */
    getList() {
      this.loading = true;
      list(this.queryParams).then(response => {
          this.coinList = response.rows;
          this.total = response.total;
          this.loading = false;
        }
      );
    },

    getProfit() {
      this.loading = true;
      profit(this.queryParams).then(response => {
          this.profitDto = response.data;
        }
      );
    },

    // 返回按钮
    handleClose() {
      const obj = { path: "/system/role" };
      this.$tab.closeOpenPage(obj);
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.queryParams.accountId = this.$route.query.accountId;
      this.getList();
      this.getProfit();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    }
  }
};
</script>
