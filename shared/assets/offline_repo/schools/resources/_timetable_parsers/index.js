import zhengfangNew from './zhengfang_new.js';
import urp from './urp.js';
import urpNew from './urp_new.js';
import kingosoft from './kingosoft.js';
import kingosoftNew from './kingosoft_new.js';
import qiangzhi from './qiangzhi.js';
import qiangzhiOld from './qiangzhi_old.js';
import wisedu from './wisedu.js';
import southSoft from './south_soft.js';
import chaoxing from './chaoxing.js';

const adapters = {
  [zhengfangNew.id]: zhengfangNew,
  [urp.id]: urp,
  [urpNew.id]: urpNew,
  [kingosoft.id]: kingosoft,
  [kingosoftNew.id]: kingosoftNew,
  [qiangzhi.id]: qiangzhi,
  [qiangzhiOld.id]: qiangzhiOld,
  [wisedu.id]: wisedu,
  [southSoft.id]: southSoft,
  [chaoxing.id]: chaoxing
};

/**
 * 根据教务系统类型获取适配器
 * @param {string} type - 教务系统类型ID
 */
export function getAdapter(type) {
  return adapters[type] || null;
}

/**
 * 获取所有已注册的适配器列表
 */
export function getAllAdapters() {
  return Object.values(adapters).map(a => ({
    id: a.id,
    name: a.name
  }));
}
