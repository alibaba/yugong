## 背景

08年左右，阿里巴巴开始尝试MySQL的相关研究，并开发了基于MySQL分库分表技术的相关产品，Cobar/TDDL(目前为阿里云DRDS产品)，解决了单机Oracle无法满足的扩展性问题，当时也掀起一股去IOE项目的浪潮，愚公这项目因此而诞生，其要解决的目标就是帮助用户完成从Oracle数据迁移到MySQL上，完成去IOE的第一步. 

## 项目介绍


名称:   yugong

译意:   愚公移山

语言:   纯java开发

定位:   数据库迁移 (目前主要支持oracle / mysql / DRDS)

## 项目介绍


整个数据迁移过程，分为两部分：

1.  全量迁移
2.  增量迁移

![](https://camo.githubusercontent.com/9a9cc09c5a7598239da20433857be61c54481b9c/687474703a2f2f646c322e69746579652e636f6d2f75706c6f61642f6174746163686d656e742f303131352f343531312f31306334666134632d626634342d333165352d623531312d6231393736643164373636392e706e67)

过程描述：

1.  增量数据收集 (创建oracle表的增量物化视图)
2.  进行全量复制
3.  进行增量复制 (可并行进行数据校验)
4.  原库停写，切到新库

## 架构


![](http://dl2.iteye.com/upload/attachment/0115/5473/8532d838-d4b2-371b-af9f-829d4127b1b8.png){width="584"
height="206"}

说明: 

1.  一个Jvm Container对应多个instance，每个instance对应于一张表的迁移任务
2.  instance分为三部分
    a.  extractor  (从源数据库上提取数据，可分为全量/增量实现)
    b.  translator  (将源库上的数据按照目标库的需求进行自定义转化)
    c.  applier  (将数据更新到目标库，可分为全量/增量/对比的实现)

## DevDesign


See the page for dev design:
[DevDesign](https://github.com/alibaba/yugong/wiki/DevDesign)

## QuickStart

See the page for quick start:
[QuickStart](https://github.com/alibaba/yugong/wiki/QuickStart)

## AdminGuide

See the page for admin deploy guide:
[AdminGuide](https://github.com/alibaba/yugong/wiki/AdminGuide)

## Performance

See the page for yugong performance :
[Performance](https://github.com/alibaba/yugong/wiki/Performance)

## 相关资料

1.  yugong简单介绍ppt: [ppt](https://github.com/alibaba/yugong/blob/master/docs/yugong_Intro.ppt?raw=true)
2.  [分布式关系型数据库服务DRDS](https://www.aliyun.com/product/drds)
    (前身为阿里巴巴公司的Cobar/TDDL的演进版本, 基本原理为MySQL分库分表)

## 问题反馈

1.  qq交流群： 537157866
2.  邮件交流： jianghang115@gmail.com, zylicfc@gmail.com
3.  新浪微博： agapple0002
4.  报告issue：[issues](https://github.com/alibaba/yugong/issues)

