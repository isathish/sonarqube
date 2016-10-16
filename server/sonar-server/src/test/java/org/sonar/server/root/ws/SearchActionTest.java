/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.root.ws;

import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.user.UserDao;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserTesting;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.WsRoot;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchActionTest {

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);
  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private UserDao userDao = dbTester.getDbClient().userDao();
  private DbSession dbSession = dbTester.getSession();
  private SearchAction underTest = new SearchAction(userSessionRule, dbTester.getDbClient());
  private WsActionTester wsTester = new WsActionTester(underTest);

  @Test
  public void verify_definition() {
    WebService.Action action = wsTester.getDef();
    assertThat(action.key()).isEqualTo("search");
    assertThat(action.isInternal()).isTrue();
    assertThat(action.isPost()).isFalse();
    assertThat(action.since()).isEqualTo("6.2");
    assertThat(action.description()).isEqualTo("Search for root user.<br/>" +
      "Requires to be root.");
    assertThat(action.responseExample()).isNull();
    assertThat(action.deprecatedKey()).isNull();
    assertThat(action.deprecatedSince()).isNull();
    assertThat(action.handler()).isSameAs(underTest);
    assertThat(action.params()).isEmpty();
  }

  @Test
  public void execute_fails_with_ForbiddenException_when_user_is_not_logged_in() {
    expectInsufficientPrivilegesForbiddenException();

    executeRequest();
  }

  @Test
  public void execute_fails_with_ForbiddenException_when_user_is_not_root() {
    userSessionRule.login();

    expectInsufficientPrivilegesForbiddenException();

    executeRequest();
  }

  @Test
  public void execute_returns_empty_list_of_root_when_DB_is_empty() {
    makeAuthenticatedUserRoot();

    assertThat(executeRequest()).isEmpty();
  }

  @Test
  public void execute_does_not_fail_when_root_user_has_neither_email_nor_name() {
    makeAuthenticatedUserRoot();
    UserDto rootDto = userDao.insert(dbSession, UserTesting.newUserDto().setName(null).setEmail(null));
    userDao.setRoot(dbSession, rootDto.getLogin(), true);
    dbSession.commit();

    List<WsRoot.Root> roots = executeRequest();
    assertThat(roots).hasSize(1);
    WsRoot.Root root = roots.iterator().next();
    assertThat(root.getLogin()).isEqualTo(rootDto.getLogin());
    assertThat(root.hasName()).isFalse();
    assertThat(root.hasEmail()).isFalse();
  }

  @Test
  public void execute_returns_root_users_sorted_by_name() {
    makeAuthenticatedUserRoot();
    userDao.insert(dbSession, UserTesting.newUserDto().setName("ddd"));
    UserDto root1 = userDao.insert(dbSession, UserTesting.newUserDto().setName("ccc"));
    userDao.setRoot(dbSession, root1.getLogin(), true);
    UserDto root2 = userDao.insert(dbSession, UserTesting.newUserDto().setName("bbb"));
    userDao.setRoot(dbSession, root2.getLogin(), true);
    userDao.insert(dbSession, UserTesting.newUserDto().setName("aaa"));
    dbSession.commit();

    assertThat(executeRequest())
      .extracting(WsRoot.Root::getName)
      .containsExactly("bbb", "ccc");
  }

  private UserSessionRule makeAuthenticatedUserRoot() {
    return userSessionRule.login().setRoot();
  }

  private List<WsRoot.Root> executeRequest() {
    TestRequest request = wsTester.newRequest()
      .setMediaType(MediaTypes.PROTOBUF);
    try {
      return WsRoot.SearchWsResponse.parseFrom(request.execute().getInputStream()).getRootsList();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void expectInsufficientPrivilegesForbiddenException() {
    expectedException.expect(ForbiddenException.class);
    expectedException.expectMessage("Insufficient privileges");
  }

}